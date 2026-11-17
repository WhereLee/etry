package com.wherelee.edu.modules.commerce.outbox;

import java.time.LocalDateTime;

/**
 * 发件箱消息。资金事务内写入意图，relay 异步投递。
 */
public record OutboxMessage(
        Long id,
        String messageType,
        Long orderId,
        String orderNo,
        Long userId,
        Long productId,
        String productType,
        String reason,
        String status,
        int retryCount,
        int maxRetry,
        String leaseToken,
        LocalDateTime leaseUntil,
        LocalDateTime nextRetryAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime processedAt
) {
    public static final String TYPE_FULFILL = "FULFILL";
    public static final String TYPE_REVOKE = "REVOKE";

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_RETRY = "RETRY";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_DEAD = "DEAD";
}