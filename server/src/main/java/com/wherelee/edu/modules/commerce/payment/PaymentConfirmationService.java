package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付确认状态机。
 *
 * <p>锁顺序固定：订单行 → attempt。事件按通知携带的 attemptId 落到具体支付尝试，
 * 最新尝试只用于判断“当前尝试”，绝不拿最新尝试替事件承担成功/失败。</p>
 *
 * <p>资金事实优先：成功可把 FAILED/SUPERSEDED 修正为 SUCCESS（旧状态留在 failure 字段与告警中）；
 * 失败只能写 PROCESSING/SUPERSEDED，不能覆盖 SUCCESS。已付款订单收到不同 trade_no 的成功
 * 视为重复付款并告警；终态/退款态订单收到成功不自动改单，交人工核对。</p>
 */
@Slf4j
@Service
public class PaymentConfirmationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AlertPublisher alertPublisher;
    private final PaymentMetrics metrics;

    public PaymentConfirmationService(OrderRepository orderRepository,
                                      PaymentRepository paymentRepository,
                                      AlertPublisher alertPublisher,
                                      PaymentMetrics metrics) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.alertPublisher = alertPublisher;
        this.metrics = metrics;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConfirmationResult confirm(PaymentNotification notification) {
        OrderRecord order = orderRepository.findByOrderNoForUpdate(notification.orderNo()).orElse(null);
        if (order == null) {
            alert("PAYMENT_UNKNOWN_ORDER", "支付通知包含未知订单 orderNo=" + notification.orderNo());
            return new ConfirmationResult(false, false, "未知订单", null);
        }

        PaymentAttemptRecord attempt = paymentRepository.findAttemptById(notification.attemptId()).orElse(null);
        if (attempt == null) {
            return reject(notification, order, "PAYMENT_UNKNOWN_ATTEMPT",
                    "支付通知包含未知支付尝试 attemptId=" + notification.attemptId());
        }
        if (!attempt.orderId().equals(order.id())) {
            return reject(notification, order, "PAYMENT_ATTEMPT_ORDER_MISMATCH",
                    "支付尝试与订单不匹配 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id() + " attemptOrderId=" + attempt.orderId());
        }
        if (!notification.provider().equals(attempt.channel())) {
            return reject(notification, order, "PAYMENT_CHANNEL_MISMATCH",
                    "支付渠道与尝试不匹配 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id() + " notifyChannel=" + notification.provider()
                            + " attemptChannel=" + attempt.channel());
        }
        if (!notification.orderNo().equals(order.orderNo())) {
            return reject(notification, order, "PAYMENT_ATTEMPT_ORDER_MISMATCH",
                    "通知订单号与尝试所属订单不匹配 orderNo=" + order.orderNo()
                            + " notifyOrderNo=" + notification.orderNo());
        }

        boolean successEvent = "PAYMENT_SUCCESS".equals(notification.eventType());
        if (successEvent && (notification.tradeNo() == null || notification.tradeNo().isBlank())) {
            return reject(notification, order, "PAYMENT_SUCCESS_MISSING_TRADE_NO",
                    "支付成功事件缺少渠道交易号 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id());
        }
        if (successEvent && (notification.providerTradeNo() == null
                || !notification.providerTradeNo().equals(attempt.providerTradeNo()))) {
            return reject(notification, order, "PAYMENT_MERCHANT_TRADE_MISMATCH",
                    "支付成功事件的商户交易号与尝试不匹配 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id()
                            + " merchantTradeNo=" + notification.providerTradeNo());
        }
        if (successEvent && notification.amountCents() != order.priceCents()) {
            metrics.amountMismatch();
            insertFailedEvent(notification, order,
                    "金额不一致 expected=" + order.priceCents() + " actual=" + notification.amountCents());
            alert("PAYMENT_AMOUNT_MISMATCH",
                    "支付金额不一致 orderNo=" + order.orderNo()
                            + " expected=" + order.priceCents()
                            + " actual=" + notification.amountCents());
            return new ConfirmationResult(false, false, "支付金额不一致，订单未发货", order);
        }

        PaymentAttemptRecord latest = paymentRepository.findLatestAttempt(order.id()).orElse(null);
        boolean currentAttempt = latest != null && latest.id().equals(attempt.id());

        boolean inserted = paymentRepository.insertEvent(
                notification.provider(), notification.eventKey(), attempt.id(),
                order.id(), order.orderNo(), notification.eventType(), notification.amountCents(),
                notification.tradeNo(), notification.providerNotifyId(), notification.payloadDigest());
        PaymentEventRecord event = paymentRepository
                .findEvent(notification.provider(), notification.eventKey())
                .orElseThrow(() -> new IllegalStateException("支付事件写入后不可读取"));
        if (!inserted) {
            metrics.callbackDuplicate();
            return new ConfirmationResult(
                    "PROCESSED".equals(event.processStatus()),
                    true,
                    "重复支付事件已忽略",
                    order
            );
        }

        if (successEvent) {
            return applySuccess(notification, order, attempt, currentAttempt, event);
        }
        return applyFailure(notification, order, attempt, currentAttempt, event);
    }

    private ConfirmationResult applySuccess(PaymentNotification notification, OrderRecord order,
                                            PaymentAttemptRecord attempt, boolean currentAttempt,
                                            PaymentEventRecord event) {
        OrderStatus status = order.status();
        if (status == OrderStatus.PAID || status == OrderStatus.FULFILLED) {
            paymentRepository.markAttemptSuccess(attempt.id(), notification.tradeNo());
            boolean sameTrade = notification.tradeNo() != null
                    && notification.tradeNo().equals(order.providerTradeNo());
            if (sameTrade) {
                paymentRepository.markEventProcessed(event.id());
                return new ConfirmationResult(true, true, "重复支付事件已忽略", order);
            }
            alert("PAYMENT_DUPLICATE_SUCCESS",
                    "同一订单收到第二笔成功付款 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id() + " attemptNo=" + attempt.attemptNo()
                            + " tradeNo=" + notification.tradeNo()
                            + " paidTradeNo=" + order.providerTradeNo()
                            + " amountCents=" + notification.amountCents());
            paymentRepository.markEventProcessed(event.id());
            return new ConfirmationResult(false, false, "重复付款已记录并告警，不重复发放权益", order);
        }
        if (status == OrderStatus.CLOSED || status == OrderStatus.REFUNDING
                || status == OrderStatus.REFUNDED || status == OrderStatus.REFUND_FAILED) {
            paymentRepository.markAttemptSuccess(attempt.id(), notification.tradeNo());
            paymentRepository.markEventManualReview(event.id(), "订单状态需人工核对：" + status);
            alert("PAYMENT_MANUAL_REVIEW",
                    "非可支付状态订单收到成功通知，需人工核对资金 orderNo=" + order.orderNo()
                            + " status=" + status + " attemptId=" + attempt.id()
                            + " tradeNo=" + notification.tradeNo()
                            + " amountCents=" + notification.amountCents());
            return new ConfirmationResult(false, false, "订单状态需人工核对，未自动改单", order);
        }
        if (status == OrderStatus.PAY_FAILED && !currentAttempt) {
            paymentRepository.markAttemptSuccess(attempt.id(), notification.tradeNo());
            paymentRepository.markEventManualReview(event.id(), "非当前尝试成功但订单为 PAY_FAILED");
            alert("PAYMENT_MANUAL_REVIEW",
                    "非当前支付尝试成功，需人工核对资金 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id() + " tradeNo=" + notification.tradeNo());
            return new ConfirmationResult(false, false, "订单状态需人工核对，未自动改单", order);
        }
        if (status != OrderStatus.PAYING && status != OrderStatus.PAY_FAILED) {
            paymentRepository.markEventFailed(event.id(), "订单状态不允许支付确认：" + status);
            return new ConfirmationResult(false, false, "订单状态不允许支付确认", order);
        }

        paymentRepository.markAttemptSuccess(attempt.id(), notification.tradeNo());
        if (!currentAttempt) {
            alert("PAYMENT_LATE_SUCCESS",
                    "旧支付尝试迟到成功，资金事实优先 orderNo=" + order.orderNo()
                            + " attemptId=" + attempt.id() + " attemptNo=" + attempt.attemptNo()
                            + " previousStatus=" + attempt.status()
                            + " tradeNo=" + notification.tradeNo());
        }
        paymentRepository.markOtherProcessingSuperseded(order.id(), attempt.id());
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

    private ConfirmationResult applyFailure(PaymentNotification notification, OrderRecord order,
                                            PaymentAttemptRecord attempt, boolean currentAttempt,
                                            PaymentEventRecord event) {
        paymentRepository.markAttemptFailed(attempt.id(), "PROVIDER_FAILED", "支付平台返回失败");
        if (currentAttempt && order.status() == OrderStatus.PAYING
                && !"SUCCESS".equals(attempt.status())) {
            orderRepository.markPayFailed(order.orderNo(), "支付平台返回失败");
            metrics.paymentFailed();
        }
        paymentRepository.markEventProcessed(event.id());
        return new ConfirmationResult(true, false, "支付失败已入账",
                orderRepository.findByOrderNo(order.orderNo()).orElse(order));
    }

    /**
     * 归属/渠道/金额校验失败：记录 FAILED 事件并告警，不修改任何资金状态。
     */
    private ConfirmationResult reject(PaymentNotification notification, OrderRecord order,
                                      String alertType, String reason) {
        insertFailedEvent(notification, order, reason);
        alert(alertType, reason + " orderNo=" + order.orderNo());
        return new ConfirmationResult(false, false, reason, order);
    }

    private void insertFailedEvent(PaymentNotification notification, OrderRecord order, String reason) {
        boolean inserted = paymentRepository.insertEvent(
                notification.provider(), notification.eventKey(), notification.attemptId(),
                order.id(), order.orderNo(), notification.eventType(), notification.amountCents(),
                notification.tradeNo(), notification.providerNotifyId(), notification.payloadDigest());
        if (inserted) {
            paymentRepository.findEvent(notification.provider(), notification.eventKey())
                    .ifPresent(event -> paymentRepository.markEventFailed(event.id(), reason));
        } else {
            // event_key 已被占用（可能是已 PROCESSED 的合法事件）：保留原状态，绝不覆盖
            metrics.callbackCollision();
            log.warn("payment_event_collision provider={} eventKey={} orderNo={} attemptId={} reason={}",
                    notification.provider(), notification.eventKey(), order.orderNo(),
                    notification.attemptId(), reason);
        }
    }

    private void alert(String type, String message) {
        try {
            alertPublisher.publish(type, "ERROR", message, false, "webhook delivery not configured for payment alerts");
        } catch (Exception e) {
            log.warn("支付告警入库失败 type={} message={}", type, message, e);
        }
    }

    public record ConfirmationResult(boolean success, boolean duplicate, String message, OrderRecord order) {
    }
}
