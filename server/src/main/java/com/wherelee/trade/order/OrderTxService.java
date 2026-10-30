package com.wherelee.trade.order;

import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.order.dto.CreateOrderRequest;
import com.wherelee.trade.point.PointAccount;
import com.wherelee.trade.point.PointAccountMapper;
import com.wherelee.trade.point.PointFlow;
import com.wherelee.trade.point.PointFlowMapper;
import com.wherelee.trade.point.PointFlowType;
import com.wherelee.trade.product.Product;
import com.wherelee.trade.product.ProductMapper;
import com.wherelee.trade.stock.StockLog;
import com.wherelee.trade.stock.StockLogMapper;
import com.wherelee.trade.stock.StockLogType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 下单的事务边界。
 * 与 OrderService 分开是为了让幂等兜底（捕获唯一键冲突）发生在事务之外，
 * 避免在已回滚的事务里继续操作。
 */
@Service
@RequiredArgsConstructor
public class OrderTxService {

    private final ProductMapper productMapper;
    private final PointAccountMapper pointAccountMapper;
    private final PointFlowMapper pointFlowMapper;
    private final StockLogMapper stockLogMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderProperties orderProperties;

    /**
     * 全流程在一个本地事务内：冻积分 -> 冻库存 -> 建订单 -> 写两本流水。
     * 任何一步抛异常，前面的冻结由数据库回滚撤销，不需要补偿代码。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order create(Long userId, String idempotencyKey, CreateOrderRequest request) {
        // 1. 合并同商品数量；TreeMap 保证按商品 ID 升序，固定加锁顺序以防死锁
        Map<Long, Integer> quantityByProduct = new TreeMap<>();
        for (CreateOrderRequest.Item item : request.items()) {
            quantityByProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }
        List<Long> productIds = new ArrayList<>(quantityByProduct.keySet());

        // 2. 批量查商品
        List<Product> products = productMapper.findByIds(productIds);
        if (products.size() != productIds.size()) {
            throw BizException.of(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 3. 校验状态与可售库存并累计总积分。这里只为尽早失败，真正的并发保护在下面的 CAS 更新
        long totalPoints = 0L;
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            int quantity = quantityByProduct.get(productId);
            if (!Product.STATUS_ON_SALE.equals(product.getStatus())) {
                throw new BizException(ErrorCode.PRODUCT_NOT_ON_SALE, product.getName() + " 已下架");
            }
            if (product.availableStock() < quantity) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, product.getName() + " 库存不足");
            }
            totalPoints += product.getPricePoints() * quantity;
        }

        // 4. 冻结积分：单行 CAS，失败即余额不足
        if (pointAccountMapper.freeze(userId, totalPoints) == 0) {
            throw BizException.of(ErrorCode.POINT_NOT_ENOUGH);
        }

        // 5. 冻结库存：逐商品 CAS，顺序与第 1 步一致
        for (Long productId : productIds) {
            int quantity = quantityByProduct.get(productId);
            if (productMapper.reserveStock(productId, quantity) == 0) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                        productMap.get(productId).getName() + " 库存不足");
            }
        }

        // 6. 建订单
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.next());
        order.setUserId(userId);
        order.setTotalPoints(totalPoints);
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setAddress(request.address());
        order.setIdempotencyKey(idempotencyKey);
        order.setExpireAt(now.plusMinutes(orderProperties.expireMinutes()));
        orderMapper.insert(order);

        // 7. 明细快照 + 库存流水
        List<OrderItem> items = new ArrayList<>(productIds.size());
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            int quantity = quantityByProduct.get(productId);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(productId);
            item.setProductName(product.getName());
            item.setUnitPoints(product.getPricePoints());
            item.setQuantity(quantity);
            item.setSubtotalPoints(product.getPricePoints() * quantity);
            items.add(item);

            StockLog stockLog = new StockLog();
            stockLog.setProductId(productId);
            stockLog.setOrderId(order.getId());
            stockLog.setType(StockLogType.RESERVE.name());
            stockLog.setQuantity(quantity);
            stockLogMapper.insert(stockLog);
        }
        orderItemMapper.batchInsert(items);

        // 8. 积分流水，记下冻结后的可用余额
        PointAccount account = pointAccountMapper.findByUserId(userId);
        PointFlow flow = new PointFlow();
        flow.setUserId(userId);
        flow.setOrderId(order.getId());
        flow.setType(PointFlowType.FREEZE.name());
        flow.setAmount(-totalPoints);
        flow.setBalanceAfter(account.getBalance());
        pointFlowMapper.insert(flow);

        return order;
    }
}
