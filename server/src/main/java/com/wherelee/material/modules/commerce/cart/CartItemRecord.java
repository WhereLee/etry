package com.wherelee.material.modules.commerce.cart;

import java.time.LocalDateTime;

/**
 * 购物车行。电子资料数量恒 1，唯一约束 (user_id, material_id)。
 */
public record CartItemRecord(
        Long id,
        Long userId,
        Long materialId,
        LocalDateTime createdAt
) {
}