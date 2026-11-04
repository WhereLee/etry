package com.wherelee.edu.payment;

import com.wherelee.edu.alert.AlertLogRepository;
import com.wherelee.edu.entitlement.EntitlementRepository;
import com.wherelee.edu.order.OrderRecord;
import com.wherelee.edu.order.OrderRepository;
import com.wherelee.edu.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntitlementRepository entitlementRepository;
    private final TransactionTemplate transactionTemplate;
    private final PaymentMetrics metrics;
    private final AlertLogRepository alertLogRepository;

    public OrderFulfillmentService(OrderRepository orderRepository,
                                   PaymentRepository paymentRepository,
                                   EntitlementRepository entitlementRepository,
                                   PlatformTransactionManager transactionManager,
                                   PaymentMetrics metrics,
                                   AlertLogRepository alertLogRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        this.alertLogRepository = alertLogRepository;
    }

    public boolean fulfill(String orderNo) {
        OrderRecord order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null || order.status() == OrderStatus.FULFILLED) {
            return true;
        }
        if (order.status() != OrderStatus.PAID) {
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
            transactionTemplate.executeWithoutResult(status -> {
                entitlementRepository.grantOrder(order.userId(), order.seriesId(), order.id());
                int changed = orderRepository.markFulfilled(orderNo);
                if (changed == 0) {
                    OrderRecord current = orderRepository.findByOrderNo(orderNo).orElse(order);
                    if (current.status() != OrderStatus.FULFILLED) {
                        throw new IllegalStateException("订单履约状态更新失败：" + current.status());
                    }
                }
            });
            metrics.fulfillmentSucceeded();
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
            alertLogRepository.insert("PAYMENT_FULFILLMENT_FAILED", "ERROR",
                    "订单履约失败 orderNo=" + orderNo + " reason=" + message, false,
                    "webhook delivery not configured for payment alerts");
        } catch (Exception ignored) {
        }
    }
}
