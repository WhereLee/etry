package com.wherelee.material.modules.commerce.order;

import java.time.LocalDateTime;

public record RefundRequestRecord(
        Long id,
        Long orderId,
        String orderNo,
        Long userId,
        String reason,
        String status,
        Long handledBy,
        LocalDateTime handledAt,
        String handleNote,
        LocalDateTime createdAt
) {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
}