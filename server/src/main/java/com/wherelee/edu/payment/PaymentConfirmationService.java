package com.wherelee.edu.payment;

import com.wherelee.edu.alert.AlertLogRepository;
import com.wherelee.edu.order.OrderRecord;
import com.wherelee.edu.order.OrderRepository;
import com.wherelee.edu.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentConfirmationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AlertLogRepository alertLogRepository;
    private final PaymentMetrics metrics;

    public PaymentConfirmationService(OrderRepository orderRepository,
                                      PaymentRepository paymentRepository,
                                      AlertLogRepository alertLogRepository,
                                      PaymentMetrics metrics) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.alertLogRepository = alertLogRepository;
        this.metrics = metrics;
    }

    @Transactional
    public ConfirmationResult confirm(PaymentNotification notification) {
        OrderRecord order = orderRepository.findByOrderNo(notification.orderNo()).orElse(null);
        if (order == null) {
            alert("PAYMENT_UNKNOWN_ORDER", "支付通知包含未知订单 orderNo=" + notification.orderNo());
            return new ConfirmationResult(false, false, "未知订单", null);
        }

        boolean successEvent = "PAYMENT_SUCCESS".equals(notification.eventType());
        if (successEvent && notification.amountCents() != order.priceCents()) {
            metrics.amountMismatch();
            paymentRepository.insertEvent(notification.provider(), notification.eventKey(),
                    order.id(), order.orderNo(), notification.eventType(),
                    notification.amountCents(), null);
            paymentRepository.findEvent(notification.provider(), notification.eventKey())
                    .ifPresent(event -> paymentRepository.markEventFailed(event.id(),
                            "金额不一致 expected=" + order.priceCents() + " actual=" + notification.amountCents()));
            alert("PAYMENT_AMOUNT_MISMATCH",
                    "支付金额不一致 orderNo=" + order.orderNo()
                            + " expected=" + order.priceCents()
                            + " actual=" + notification.amountCents());
            return new ConfirmationResult(false, false, "支付金额不一致，订单未发货", order);
        }

        boolean inserted = paymentRepository.insertEvent(
                notification.provider(),
                notification.eventKey(),
                order.id(),
                order.orderNo(),
                notification.eventType(),
                notification.amountCents(),
                null
        );
        PaymentEventRecord event = paymentRepository
                .findEvent(notification.provider(), notification.eventKey())
                .orElseThrow(() -> new IllegalStateException("支付事件写入后不可读取"));
        if (!inserted) {
            metrics.callbackDuplicate();
            return new ConfirmationResult(
                    "PROCESSED".equals(event.processStatus()),
                    true,
                    "重复支付事件已忽略",
                    orderRepository.findByOrderNo(order.orderNo()).orElse(order)
            );
        }

        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id()).orElse(null);
        if (successEvent) {
            if (attempt != null) {
                paymentRepository.markAttemptSuccess(attempt.id());
            }
            int changed = orderRepository.markPaid(order.orderNo(), notification.tradeNo(), notification.provider());
            OrderRecord updated = orderRepository.findByOrderNo(order.orderNo()).orElse(order);
            if (changed == 0 && updated.status() != OrderStatus.PAID
                    && updated.status() != OrderStatus.FULFILLED) {
                paymentRepository.markEventFailed(event.id(), "订单状态不允许支付确认：" + updated.status());
                return new ConfirmationResult(false, false, "订单状态不允许支付确认", updated);
            }
            paymentRepository.markEventProcessed(event.id());
            metrics.paymentConfirmed();
            return new ConfirmationResult(true, false, "支付已确认", updated);
        }

        if (attempt != null) {
            paymentRepository.markAttemptFailed(attempt.id(), "PROVIDER_FAILED", "支付平台返回失败");
        }
        orderRepository.markPayFailed(order.orderNo(), "支付平台返回失败");
        paymentRepository.markEventProcessed(event.id());
        metrics.paymentFailed();
        return new ConfirmationResult(true, false, "支付失败已入账",
                orderRepository.findByOrderNo(order.orderNo()).orElse(order));
    }

    private void alert(String type, String message) {
        try {
            alertLogRepository.insert(type, "ERROR", message, false, "webhook delivery not configured for payment alerts");
        } catch (Exception e) {
            log.warn("支付告警入库失败 type={} message={}", type, message, e);
        }
    }

    public record ConfirmationResult(boolean success, boolean duplicate, String message, OrderRecord order) {
    }
}
