package com.wherelee.trade.order;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Long unitPoints;
    private Integer quantity;
    private Long subtotalPoints;
}
