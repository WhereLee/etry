package com.wherelee.trade.order.dto;

import com.wherelee.trade.order.OrderItem;

public record OrderItemVO(
        Long productId,
        String productName,
        Long unitPoints,
        Integer quantity,
        Long subtotalPoints
) {

    public static OrderItemVO from(OrderItem item) {
        return new OrderItemVO(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPoints(),
                item.getQuantity(),
                item.getSubtotalPoints());
    }
}
