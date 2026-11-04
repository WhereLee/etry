package com.wherelee.edu.order;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.entitlement.EntitlementRepository;
import com.wherelee.edu.order.dto.OrderDtos;
import com.wherelee.edu.payment.PaymentAttemptRecord;
import com.wherelee.edu.payment.PaymentEventRecord;
import com.wherelee.edu.payment.PaymentProperties;
import com.wherelee.edu.payment.PaymentRepository;
import com.wherelee.edu.payment.PaymentMetrics;
import com.wherelee.edu.payment.RefundRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CatalogRepository catalogRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntitlementRepository entitlementRepository;
    private final PaymentProperties paymentProperties;
    private final PaymentMetrics paymentMetrics;

    public OrderService(CatalogRepository catalogRepository,
                        OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        EntitlementRepository entitlementRepository,
                        PaymentProperties paymentProperties,
                        PaymentMetrics paymentMetrics) {
        this.catalogRepository = catalogRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.paymentProperties = paymentProperties;
        this.paymentMetrics = paymentMetrics;
    }

    public List<CatalogItem> catalog(Long userId) {
        return catalogRepository.findOnSale(userId);
    }

    @Transactional
    public OrderDtos.OrderView create(Long userId, OrderDtos.CreateOrderRequest request) {
        var existingRequest = orderRepository.findByClientRequest(userId, request.clientRequestId());
        if (existingRequest.isPresent()) {
            return view(existingRequest.get(), userId);
        }
        if (entitlementRepository.hasActive(userId, request.seriesId())) {
            throw new BizException(HttpStatus.CONFLICT, "你已经拥有这个课程");
        }

        var existingOrder = orderRepository.findActive(userId, request.seriesId());
        if (existingOrder.isPresent()) {
            OrderRecord order = existingOrder.get();
            if (order.expired() && order.status() != OrderStatus.PAID) {
                orderRepository.close(order.orderNo(), false);
            } else {
                return view(order, userId);
            }
        }

        SaleSeries series = catalogRepository.findPurchasable(request.seriesId())
                .orElseThrow(() -> BizException.badRequest("课程未发布、未开售或价格无效"));
        String orderNo = generateOrderNo();
        String activeKey = userId + ":" + series.id();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(Math.max(5, paymentProperties.orderExpireMinutes()));
        try {
            orderRepository.insert(orderNo, userId, series.id(), series.title(),
                    series.priceCents(), series.currency(), request.clientRequestId(),
                    activeKey, expireAt);
            paymentMetrics.orderCreated();
        } catch (DuplicateKeyException e) {
            return orderRepository.findByClientRequest(userId, request.clientRequestId())
                    .or(() -> orderRepository.findActive(userId, request.seriesId()))
                    .map(order -> view(order, userId))
                    .orElseThrow(() -> new BizException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试"));
        }
        return orderRepository.findByOrderNo(orderNo)
                .map(order -> view(order, userId))
                .orElseThrow(() -> new IllegalStateException("订单创建后不可读取"));
    }

    public PageResult<OrderDtos.OrderView> list(Long userId, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = orderRepository.countUserOrders(userId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        List<OrderDtos.OrderView> records = orderRepository
                .findUserOrders(userId, (safePage - 1) * safeSize, safeSize)
                .stream().map(order -> view(order, userId)).toList();
        return new PageResult<>(records, total, safePage, safeSize);
    }

    public OrderDtos.OrderDetail detail(Long userId, String orderNo) {
        OrderRecord order = requireOwned(userId, orderNo);
        List<OrderDtos.TimelineItem> timeline = timeline(order);
        return new OrderDtos.OrderDetail(view(order, userId), timeline);
    }

    public OrderDtos.OrderDetail detailForAdmin(String orderNo) {
        OrderRecord order = require(orderNo);
        return new OrderDtos.OrderDetail(view(order, order.userId()), timeline(order));
    }

    @Transactional
    public OrderDtos.OrderView close(Long userId, String orderNo) {
        OrderRecord order = requireOwned(userId, orderNo);
        if (order.status() == OrderStatus.PAID || order.status() == OrderStatus.FULFILLED) {
            throw BizException.badRequest("已支付订单不能直接关闭");
        }
        if (order.status() == OrderStatus.PAYING) {
            throw BizException.badRequest("支付中订单不能直接关闭，请先查询支付结果");
        }
        orderRepository.close(orderNo, true);
        return orderRepository.findByOrderNo(orderNo).map(value -> view(value, userId))
                .orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    public OrderRecord requireOwned(Long userId, String orderNo) {
        return orderRepository.findOwned(orderNo, userId)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    public OrderRecord require(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
    }

    public OrderDtos.OrderView view(OrderRecord order, Long userId) {
        return new OrderDtos.OrderView(
                order.orderNo(),
                order.seriesId(),
                order.seriesTitle(),
                order.priceCents(),
                order.currency(),
                order.status().name(),
                statusLabel(order.status()),
                order.payChannel(),
                order.expireAt(),
                order.paidAt(),
                order.fulfilledAt(),
                order.createdAt(),
                entitlementRepository.hasActive(userId, order.seriesId())
        );
    }

    private List<OrderDtos.TimelineItem> timeline(OrderRecord order) {
        List<OrderDtos.TimelineItem> items = new ArrayList<>();
        items.add(new OrderDtos.TimelineItem("CREATED", "订单已创建", order.createdAt()));
        List<PaymentAttemptRecord> attempts = paymentRepository.findAttempts(order.id());
        for (PaymentAttemptRecord attempt : attempts) {
            items.add(new OrderDtos.TimelineItem(
                    "PAY_ATTEMPT",
                    "发起第 " + attempt.attemptNo() + " 次支付",
                    attempt.createdAt()
            ));
        }
        List<PaymentEventRecord> events = paymentRepository.findEvents(order.id());
        for (PaymentEventRecord event : events) {
            items.add(new OrderDtos.TimelineItem(
                    "PAYMENT_EVENT",
                    "支付平台事件：" + event.eventType(),
                    event.receivedAt()
            ));
        }
        if (order.paidAt() != null) {
            items.add(new OrderDtos.TimelineItem("PAID", "支付已确认", order.paidAt()));
        }
        if (order.fulfilledAt() != null) {
            items.add(new OrderDtos.TimelineItem("FULFILLED", "课程权益已发放", order.fulfilledAt()));
        }
        if (order.closedAt() != null) {
            items.add(new OrderDtos.TimelineItem("CLOSED", "订单已关闭", order.closedAt()));
        }
        paymentRepository.findRefundByOrder(order.id()).ifPresent(refund -> {
            items.add(new OrderDtos.TimelineItem("REFUND", "发起退款", refund.requestedAt()));
            if (refund.succeededAt() != null) {
                items.add(new OrderDtos.TimelineItem("REFUNDED", "退款成功", refund.succeededAt()));
            }
        });
        return items.stream()
                .sorted(Comparator.comparing(OrderDtos.TimelineItem::time,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public String statusLabel(OrderStatus status) {
        return switch (status) {
            case CREATED -> "待支付";
            case PAYING -> "支付中";
            case PAID -> "已支付，发放中";
            case FULFILLED -> "已完成";
            case PAY_FAILED -> "支付失败";
            case CLOSED -> "已关闭";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
            case REFUND_FAILED -> "退款失败";
        };
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
