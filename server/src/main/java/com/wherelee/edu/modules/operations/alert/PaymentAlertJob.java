package com.wherelee.edu.modules.operations.alert;

import com.wherelee.edu.modules.commerce.payment.PaymentProperties;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.platform.resilience.LocalFallbackCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 支付/退款异常巡检。与播放告警分离，避免把交易告警混入播放日志统计。
 */
@Slf4j
@Component
public class PaymentAlertJob {

    private final AlertProperties properties;
    private final PaymentProperties paymentProperties;
    private final PaymentRepository paymentRepository;
    private final AlertNotifier notifier;
    private final AlertPublisher alertPublisher;
    private final LocalFallbackCache dedupCache;

    public PaymentAlertJob(AlertProperties properties,
                           PaymentProperties paymentProperties,
                           PaymentRepository paymentRepository,
                           AlertNotifier notifier,
                           AlertPublisher alertPublisher,
                           LocalFallbackCache dedupCache) {
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.paymentRepository = paymentRepository;
        this.notifier = notifier;
        this.alertPublisher = alertPublisher;
        this.dedupCache = dedupCache;
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
        if (paidStuck > 0) {
            emit("PAYMENT_PAID_UNFULFILLED", "HIGH",
                    "已支付但未履约订单 " + paidStuck + " 笔");
        }
        long payingStuck = paymentRepository.countPayingBefore(
                now.minusMinutes(properties.safePaymentPayingStuckMinutes()));
        if (payingStuck > 0) {
            emit("PAYMENT_PAYING_STUCK", "MEDIUM",
                    "支付中超过 " + properties.safePaymentPayingStuckMinutes() + " 分钟订单 " + payingStuck + " 笔");
        }
        long refundExhausted = paymentRepository.countRefundRetryExhausted(
                paymentProperties.safeRefundMaxAttempts());
        if (refundExhausted > 0 && refundExhausted >= properties.safePaymentRefundRetryExhaustedThreshold()) {
            emit("PAYMENT_REFUND_RETRY_EXHAUSTED", "HIGH",
                    "退款重试耗尽订单 " + refundExhausted + " 笔");
        }
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
