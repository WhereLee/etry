package com.wherelee.edu.modules.commerce.payment;

import java.time.LocalDateTime;

public record PaymentEventRecord(
        Long id,
        String provider,
        String eventKey,
        Long orderId,
        String orderNo,
        String eventType,
        long amountCents,
        String processStatus,
        String errorMessage,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {
}
