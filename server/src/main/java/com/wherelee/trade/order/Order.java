package com.wherelee.trade.order;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Order {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long totalPoints;
    private String status;
    private String address;
    private String idempotencyKey;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
