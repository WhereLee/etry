package com.wherelee.edu.order;

import java.time.LocalDateTime;

public record OrderRecord(
        Long id,
        String orderNo,
        Long userId,
        Long seriesId,
        String seriesTitle,
        long priceCents,
        String currency,
        OrderStatus status,
        String payChannel,
        String providerTradeNo,
        String clientRequestId,
        String activeKey,
        LocalDateTime expireAt,
        LocalDateTime paidAt,
        LocalDateTime fulfilledAt,
        LocalDateTime closedAt,
        int fulfillmentAttempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean expired() {
        return expireAt != null && expireAt.isBefore(LocalDateTime.now());
    }
}
