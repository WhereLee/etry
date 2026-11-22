package com.wherelee.material.modules.commerce.cart;

import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.api.SellableCatalog;
import com.wherelee.material.modules.commerce.order.OrderItemRecord;
import com.wherelee.material.modules.commerce.order.OrderItemRepository;
import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import com.wherelee.material.modules.commerce.order.OrderService;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.payment.PaymentMetrics;
import com.wherelee.material.modules.commerce.payment.PaymentProperties;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.platform.common.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车结算：勾选行 → 多商品订单（头 + t_order_item 快照）。
 * 幂等双保险：client_request_id 唯一键 + 进行中订单含同资料时直接复用。
 * 商品可售性与价格只经 SellableCatalog(MATERIAL) 端口，已购行拒绝结算。
 */
@Service
public class CartCheckoutService {

    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final EntitlementQuery entitlementQuery;
    private final PaymentProperties paymentProperties;
    private final PaymentMetrics paymentMetrics;
    private final Map<String, SellableCatalog> catalogs;

    public CartCheckoutService(CartRepository cartRepository,
                               OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               OrderService orderService,
                               EntitlementQuery entitlementQuery,
                               PaymentProperties paymentProperties,
                               PaymentMetrics paymentMetrics,
                               List<SellableCatalog> catalogList) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.entitlementQuery = entitlementQuery;
        this.paymentProperties = paymentProperties;
        this.paymentMetrics = paymentMetrics;
        this.catalogs = catalogList.stream()
                .collect(Collectors.toMap(SellableCatalog::productType, Function.identity()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDtos.OrderView checkout(Long userId, CartDtos.CheckoutRequest request) {
        List<Long> materialIds = request.materialIds().stream().distinct().toList();

        var existingRequest = orderRepository.findByClientRequest(userId, request.clientRequestId());
        if (existingRequest.isPresent()) {
            return orderService.view(existingRequest.get(), userId);
        }

        Set<Long> inCart = cartRepository.findByUser(userId).stream()
                .map(CartItemRecord::materialId)
                .collect(Collectors.toSet());
        if (!inCart.containsAll(materialIds)) {
            throw BizException.badRequest("请先将资料加入购物车");
        }

        var existingOrder = orderRepository.findActiveByMaterialIds(userId, materialIds);
        if (existingOrder.isPresent()) {
            OrderRecord order = existingOrder.get();
            if (order.expired() && order.status() != OrderStatus.PAID) {
                orderRepository.close(order.orderNo(), false);
            } else {
                return orderService.view(order, userId);
            }
        }

        SellableCatalog catalog = catalogs.get(ProductTypes.MATERIAL);
        if (catalog == null) {
            throw new IllegalStateException("缺少可售商品目录适配器：" + ProductTypes.MATERIAL);
        }
        List<OrderItemRecord> items = new ArrayList<>();
        long total = 0;
        String currency = null;
        for (Long materialId : materialIds) {
            SellableCatalog.SellableProduct product = catalog.findPurchasable(materialId)
                    .orElseThrow(() -> BizException.badRequest("资料已下架或不可售，请刷新购物车"));
            if (entitlementQuery.hasActive(userId, materialId, ProductTypes.MATERIAL)) {
                throw BizException.badRequest("您已购买其中部分资料，请刷新购物车");
            }
            if (currency == null) {
                currency = product.currency();
            } else if (!currency.equals(product.currency())) {
                throw BizException.badRequest("所选资料币种不一致，无法合并结算");
            }
            total += product.priceCents();
            items.add(new OrderItemRecord(null, null, materialId, product.title(), product.priceCents(), null));
        }

        String orderNo = generateOrderNo();
        String title = "资料订单 共" + items.size() + "件";
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(Math.max(5, paymentProperties.orderExpireMinutes()));
        try {
            Long orderId = orderRepository.insertCartOrder(orderNo, userId, title, total, currency,
                    request.clientRequestId(), expireAt);
            orderItemRepository.insertAll(orderId, items);
            cartRepository.removeByUserAndMaterials(userId, materialIds);
            paymentMetrics.orderCreated();
        } catch (DuplicateKeyException e) {
            return orderRepository.findByClientRequest(userId, request.clientRequestId())
                    .or(() -> orderRepository.findActiveByMaterialIds(userId, materialIds))
                    .map(order -> orderService.view(order, userId))
                    .orElseThrow(() -> new BizException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试"));
        }
        return orderRepository.findByOrderNo(orderNo)
                .map(order -> orderService.view(order, userId))
                .orElseThrow(() -> new IllegalStateException("订单创建后不可读取"));
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}