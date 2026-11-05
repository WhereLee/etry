package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.commerce.entitlement.EntitlementRepository;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderTransitionPolicy;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.platform.tracing.TraceSpans;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Slf4j
@Service
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntitlementRepository entitlementRepository;
    private final TransactionTemplate transactionTemplate;
    private final PaymentMetrics metrics;
    private final AlertPublisher alertPublisher;
    private final TraceSpans traceSpans;

    public OrderFulfillmentService(OrderRepository orderRepository,
                                   PaymentRepository paymentRepository,
                                   EntitlementRepository entitlementRepository,
                                   PlatformTransactionManager transactionManager,
                                   PaymentMetrics metrics,
                                   AlertPublisher alertPublisher,
                                   TraceSpans traceSpans) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        this.alertPublisher = alertPublisher;
        this.traceSpans = traceSpans;
    }

    public boolean fulfill(String orderNo) {
        return traceSpans.trace("order.fulfill", TraceSpans.tags("order.no", orderNo), () -> fulfillInternal(orderNo));
    }

    private boolean fulfillInternal(String orderNo) {
        OrderRecord order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null || order.status() == OrderStatus.FULFILLED) {
            return true;
        }
        if (!OrderTransitionPolicy.isFulfillable(order.status())) {
            return false;
        }

        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id()).orElse(null);
        boolean failOnce = attempt != null
                && "FULFILLMENT_FAIL_ONCE".equals(attempt.mockScenario())
                && order.fulfillmentAttempts() == 0;
        if (failOnce) {
            metrics.fulfillmentFailed();
            orderRepository.incrementFulfillmentAttempts(orderNo, "Mock：模拟首次履约失败");
            alertAlert(orderNo, "Mock 模拟首次履约失败");
            log.warn("Mock 模拟首次履约失败，等待补偿任务重试 orderNo={}", orderNo);
            return false;
        }

        try {
            Boolean fulfilled = transactionTemplate.execute(status -> {
                OrderRecord locked = orderRepository.findByOrderNoForUpdate(orderNo).orElse(null);
                if (locked == null || locked.status() == OrderStatus.FULFILLED) {
                    return true;
                }
                if (!OrderTransitionPolicy.isFulfillable(locked.status())) {
                    log.warn("订单已进入不可履约状态，跳过履约 orderNo={} status={}", orderNo, locked.status());
                    return false;
                }
                entitlementRepository.grantOrder(locked.userId(), locked.seriesId(), locked.id());
                int changed = orderRepository.markFulfilled(orderNo);
                if (changed == 0) {
                    OrderRecord current = orderRepository.findByOrderNo(orderNo).orElse(locked);
                    if (current.status() == OrderStatus.FULFILLED) {
                        return true;
                    }
                    throw new IllegalStateException("订单履约状态更新失败：" + current.status());
                }
                return true;
            });
            if (!Boolean.TRUE.equals(fulfilled)) {
                return false;
            }
            metrics.fulfillmentSucceeded();
            log.info("order_fulfilled orderNo={} userId={} seriesId={}",
                    orderNo, order.userId(), order.seriesId());
            return true;
        } catch (Exception e) {
            metrics.fulfillmentFailed();
            orderRepository.incrementFulfillmentAttempts(orderNo, truncate(e.getMessage()));
            alertAlert(orderNo, truncate(e.getMessage()));
            log.error("订单履约失败 orderNo={}", orderNo, e);
            return false;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "履约失败";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void alertAlert(String orderNo, String message) {
        try {
            alertPublisher.publish("PAYMENT_FULFILLMENT_FAILED", "ERROR",
                    "订单履约失败 orderNo=" + orderNo + " reason=" + message, false,
                    "webhook delivery not configured for payment alerts");
        } catch (Exception ignored) {
        }
    }
}
