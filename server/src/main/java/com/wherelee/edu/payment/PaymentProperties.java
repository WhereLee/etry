package com.wherelee.edu.payment;

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
        long reconcileInitialDelayMs
) {
}
