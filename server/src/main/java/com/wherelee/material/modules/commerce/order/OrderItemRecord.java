package com.wherelee.material.modules.commerce.order;

import java.time.LocalDateTime;

/**
 * 订单明细行：下单时的标题/价格快照，退款与展示以快照为准。
 */
public record OrderItemRecord(
        Long id,
        Long orderId,
        Long materialId,
        String titleSnapshot,
        long priceCents,
        LocalDateTime createdAt
) {
}