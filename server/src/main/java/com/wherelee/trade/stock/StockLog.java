package com.wherelee.trade.stock;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class StockLog {

    private Long id;
    private Long productId;
    private Long orderId;
    private String type;
    private Integer quantity;
    private LocalDateTime createdAt;
}
