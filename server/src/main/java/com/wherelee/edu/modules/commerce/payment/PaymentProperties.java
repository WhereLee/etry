package com.wherelee.edu.modules.commerce.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String provider,
        boolean mockEnabled,
        String mockSecret,
        int orderExpireMinutes,
        int queryAfterSeconds,
        int reconcileBatchSize,
        long reconcileIntervalMs,
        long reconcileInitialDelayMs,
        int refundMaxAttempts,
        int refundRetryBaseSeconds
) {
    public int safeRefundMaxAttempts() {
        return Math.max(1, Math.min(refundMaxAttempts, 20));
    }

    public int safeRefundRetryBaseSeconds() {
        return Math.max(5, Math.min(refundRetryBaseSeconds, 3600));
    }
}