package com.wherelee.edu.modules.operations.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alert")
public record AlertProperties(
        boolean enabled,
        String webhookUrl,
        int windowMinutes,
        int errorThreshold,
        int stallThreshold,
        double cacheMissRateThreshold,
        int minCacheRequests,
        int dedupMinutes,
        long evaluateIntervalMs,
        long initialDelayMs,
        int paymentPaidUnfulfilledMinutes,
        int paymentPayingStuckMinutes,
        int paymentRefundRetryExhaustedThreshold
) {
    public int safePaymentPaidUnfulfilledMinutes() {
        return Math.max(1, paymentPaidUnfulfilledMinutes);
    }

    public int safePaymentPayingStuckMinutes() {
        return Math.max(1, paymentPayingStuckMinutes);
    }

    public int safePaymentRefundRetryExhaustedThreshold() {
        return Math.max(1, paymentRefundRetryExhaustedThreshold);
    }
}
