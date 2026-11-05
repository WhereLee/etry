package com.wherelee.edu.modules.commerce.payment;

import java.time.LocalDateTime;

public record RefundRecord(
        Long id,
        String refundNo,
        Long orderId,
        long amountCents,
        String status,
        int attemptCount,
        LocalDateTime nextRetryAt,
        String providerRefundNo,
        String reason,
        Long requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime succeededAt,
        String errorMessage
) {
}