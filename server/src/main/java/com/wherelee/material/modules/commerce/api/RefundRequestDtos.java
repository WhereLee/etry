package com.wherelee.material.modules.commerce.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 买家整单退款申请契约（PRD §2 买家「申请整单退款」、客服「处理退款申请」）。
 */
public final class RefundRequestDtos {

    private RefundRequestDtos() {
    }

    public record SubmitRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record RejectRequest(@Size(max = 500) String note) {
    }

    public record BuyerView(
            Long id,
            String orderNo,
            String reason,
            String status,
            LocalDateTime createdAt,
            LocalDateTime handledAt,
            String handleNote
    ) {
    }

    public record AdminView(
            Long id,
            String orderNo,
            String username,
            String nickname,
            String orderStatus,
            long priceCents,
            String reason,
            String status,
            LocalDateTime createdAt,
            Long handledBy,
            LocalDateTime handledAt,
            String handleNote
    ) {
    }
}