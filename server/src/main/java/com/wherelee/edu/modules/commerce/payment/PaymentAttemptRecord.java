package com.wherelee.edu.modules.commerce.payment;

import java.time.LocalDateTime;

public record PaymentAttemptRecord(
        Long id,
        Long orderId,
        int attemptNo,
        String channel,
        String status,
        String providerTradeNo,
        String providerStatus,
        long providerAmountCents,
        String requestDigest,
        String failureCode,
        String failureMessage,
        String mockScenario,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
