package com.wherelee.material.modules.commerce.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事务性发件箱 relay 参数。均为可配置技术默认值，非业务规则。
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        long intervalMs,
        long initialDelayMs,
        int batchSize,
        int leaseSeconds,
        int maxRetry,
        int retryBaseSeconds
) {
    public int safeBatchSize() {
        return Math.max(1, Math.min(batchSize, 500));
    }

    public int safeLeaseSeconds() {
        return Math.max(10, Math.min(leaseSeconds, 3600));
    }

    public int safeMaxRetry() {
        return Math.max(1, Math.min(maxRetry, 20));
    }

    public int safeRetryBaseSeconds() {
        return Math.max(1, Math.min(retryBaseSeconds, 3600));
    }
}