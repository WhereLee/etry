package com.wherelee.edu.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record MockCashierView(
            String orderNo,
            String seriesTitle,
            long priceCents,
            String currency,
            String status,
            List<String> scenarios
    ) {
    }

    public record MockEventRequest(
            @NotBlank(message = "请选择模拟场景") @Size(max = 40, message = "模拟场景过长") String scenario
    ) {
    }

    public record MockEventResponse(String orderNo, String orderStatus, String message) {
    }

    public record RefundRequest(
            @NotBlank(message = "请填写退款原因") @Size(max = 200, message = "退款原因过长") String reason,
            boolean simulateFailure
    ) {
    }

    public record AttemptView(
            int attemptNo,
            String status,
            String providerTradeNo,
            String providerStatus,
            String mockScenario,
            LocalDateTime createdAt,
            LocalDateTime finishedAt
    ) {
    }

    public record EventView(
            String eventKey,
            String eventType,
            long amountCents,
            String processStatus,
            String errorMessage,
            LocalDateTime receivedAt,
            LocalDateTime processedAt
    ) {
    }

    public record RefundView(
            String refundNo,
            long amountCents,
            String status,
            String reason,
            LocalDateTime requestedAt,
            LocalDateTime succeededAt,
            String errorMessage
    ) {
    }
}
