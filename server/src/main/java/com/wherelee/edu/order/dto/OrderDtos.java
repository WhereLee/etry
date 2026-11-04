package com.wherelee.edu.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotNull(message = "请选择课程") Long seriesId,
            @NotBlank(message = "缺少请求标识") @Size(max = 64, message = "请求标识过长") String clientRequestId
    ) {
    }

    public record OrderView(
            String orderNo,
            Long seriesId,
            String seriesTitle,
            long priceCents,
            String currency,
            String status,
            String statusLabel,
            String payChannel,
            LocalDateTime expireAt,
            LocalDateTime paidAt,
            LocalDateTime fulfilledAt,
            LocalDateTime createdAt,
            boolean owned
    ) {
    }

    public record PayResponse(String orderNo, String status, String channel, String cashierUrl) {
    }

    public record TimelineItem(String type, String title, LocalDateTime time) {
    }

    public record OrderDetail(OrderView order, List<TimelineItem> timeline) {
    }
}
