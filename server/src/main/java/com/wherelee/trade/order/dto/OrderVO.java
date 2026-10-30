package com.wherelee.trade.order.dto;

import com.wherelee.trade.order.Order;
import com.wherelee.trade.order.OrderItem;

import java.time.LocalDateTime;
import java.util.List;

public record OrderVO(
        Long id,
        String orderNo,
        Long totalPoints,
        String status,
        String address,
        LocalDateTime expireAt,
        LocalDateTime createdAt,
        List<OrderItemVO> items
) {

    public static OrderVO of(Order order, List<OrderItem> items) {
        return new OrderVO(
                order.getId(),
                order.getOrderNo(),
                order.getTotalPoints(),
                order.getStatus(),
                order.getAddress(),
                order.getExpireAt(),
                order.getCreatedAt(),
                items.stream().map(OrderItemVO::from).toList());
    }
}
