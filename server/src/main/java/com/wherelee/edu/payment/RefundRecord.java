package com.wherelee.edu.payment;

import java.time.LocalDateTime;

public record RefundRecord(
        Long id,
        String refundNo,
        Long orderId,
        long amountCents,
        String status,
        String providerRefundNo,
        String reason,
        Long requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime succeededAt,
        String errorMessage
) {
}
