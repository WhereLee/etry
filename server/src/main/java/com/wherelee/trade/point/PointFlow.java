package com.wherelee.trade.point;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PointFlow {

    private Long id;
    private Long userId;
    private Long orderId;
    private String type;
    private Long amount;
    private Long balanceAfter;
    private LocalDateTime createdAt;
}
