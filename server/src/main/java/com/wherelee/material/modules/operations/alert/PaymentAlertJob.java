package com.wherelee.material.modules.operations.alert;

import com.wherelee.material.modules.commerce.outbox.OutboxRepository;
import com.wherelee.material.modules.commerce.payment.PaymentProperties;
import com.wherelee.material.modules.commerce.payment.PaymentRepository;
import com.wherelee.material.modules.platform.alert.AlertPublisher;
import com.wherelee.material.modules.platform.resilience.LocalFallbackCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支付/退款异常巡检。与播放告警分离，避免把交易告警混入播放日志统计。
 */
@Slf4j
@Component
public class PaymentAlertJob {

    private final AlertProperties properties;
    private final PaymentProperties paymentProperties;
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final AlertNotifier notifier;
    private final AlertPublisher alertPublisher;
    private final LocalFallbackCache dedupCache;
    private final AtomicLong paidUnfulfilledGauge;
    private final AtomicLong payingStuckGauge;
    private final AtomicLong refundRetryExhaustedGauge;
    private final AtomicLong duplicateUnrefundedGauge;
    private final AtomicLong multiSuccessManualGauge;
    private final AtomicLong outboxDeadGauge;

    public PaymentAlertJob(AlertProperties properties,
                           PaymentProperties paymentProperties,
                           PaymentRepository paymentRepository,
                           OutboxRepository outboxRepository,
                           AlertNotifier notifier,
                           AlertPublisher alertPublisher,
                           LocalFallbackCache dedupCache,
                           MeterRegistry meterRegistry) {
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.notifier = notifier;
        this.alertPublisher = alertPublisher;
        this.dedupCache = dedupCache;
        this.paidUnfulfilledGauge = gauge(meterRegistry, "btry_payment_paid_unfulfilled", "已支付未履约订单数");
        this.payingStuckGauge = gauge(meterRegistry, "btry_payment_paying_stuck", "支付中超时订单数");
        this.refundRetryExhaustedGauge = gauge(meterRegistry, "btry_payment_refund_retry_exhausted", "退款重试耗尽订单数");
        this.duplicateUnrefundedGauge = gauge(meterRegistry, "btry_payment_duplicate_unrefunded", "重复支付未退款订单数");
        this.multiSuccessManualGauge = gauge(meterRegistry, "btry_payment_multi_success_manual", "多笔成功缺渠道单号订单数");
        this.outboxDeadGauge = gauge(meterRegistry, "btry_outbox_dead", "发件箱 DEAD 消息数");
    }

    private static AtomicLong gauge(MeterRegistry registry, String name, String description) {
        AtomicLong value = new AtomicLong();
        Gauge.builder(name, value, AtomicLong::doubleValue).description(description).register(registry);
        return value;
    }

    @Scheduled(
            fixedDelayString = "${app.alert.evaluate-interval-ms:300000}",
            initialDelayString = "${app.alert.initial-delay-ms:60000}"
    )
    public void evaluate() {
        if (!properties.enabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long paidStuck = paymentRepository.countPaidUnfulfilledBefore(
                now.minusMinutes(properties.safePaymentPaidUnfulfilledMinutes()));
        paidUnfulfilledGauge.set(paidStuck);
        if (paidStuck > 0) {
            emit("PAYMENT_PAID_UNFULFILLED", "HIGH",
                    "已支付但未履约订单 " + paidStuck + " 笔");
        }
        long payingStuck = paymentRepository.countPayingBefore(
                now.minusMinutes(properties.safePaymentPayingStuckMinutes()));
        payingStuckGauge.set(payingStuck);
        if (payingStuck > 0) {
            emit("PAYMENT_PAYING_STUCK", "MEDIUM",
                    "支付中超过 " + properties.safePaymentPayingStuckMinutes() + " 分钟订单 " + payingStuck + " 笔");
        }
        long refundExhausted = paymentRepository.countRefundRetryExhausted(
                paymentProperties.safeRefundMaxAttempts());
        refundRetryExhaustedGauge.set(refundExhausted);
        if (refundExhausted > 0 && refundExhausted >= properties.safePaymentRefundRetryExhaustedThreshold()) {
            emit("PAYMENT_REFUND_RETRY_EXHAUSTED", "HIGH",
                    "退款重试耗尽订单 " + refundExhausted + " 笔");
        }
        long duplicateUnrefunded = paymentRepository.countUnrefundedDuplicatePayments();
        duplicateUnrefundedGauge.set(duplicateUnrefunded);
        if (duplicateUnrefunded > 0) {
            emit("PAYMENT_DUPLICATE_UNREFUNDED", "HIGH",
                    "已识别但尚未退款的重复付款 " + duplicateUnrefunded + " 笔");
        }
        long multiSuccessAmbiguous = paymentRepository.countMultiSuccessMissingChannelTradeNo();
        multiSuccessManualGauge.set(multiSuccessAmbiguous);
        if (multiSuccessAmbiguous > 0) {
            emit("PAYMENT_MULTI_SUCCESS_MANUAL", "HIGH",
                    "多笔成功支付且缺渠道交易号、需人工核对退款的订单 " + multiSuccessAmbiguous + " 笔");
        }
        outboxDeadGauge.set(outboxRepository.countByStatus("DEAD"));
    }

    private void emit(String type, String severity, String message) {
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.dedupMinutes()));
        if (!dedupCache.reserve("payment-alert:" + type, ttl)) {
            return;
        }
        AlertNotifier.DeliveryResult result = notifier.send(message);
        alertPublisher.publish(type, severity, message, result.delivered(), result.error());
        log.warn("支付告警 type={} severity={} delivered={} message={}",
                type, severity, result.delivered(), message);
    }
}
