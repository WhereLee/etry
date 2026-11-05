package com.wherelee.edu.modules.operations.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminOrderVO {
    private String orderNo;
    private Long userId;
    private String username;
    private String nickname;
    private Long seriesId;
    private String seriesTitle;
    private Long priceCents;
    private String currency;
    private String status;
    private String payChannel;
    private String providerTradeNo;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime fulfilledAt;
    private LocalDateTime closedAt;
    private Integer fulfillmentAttempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
