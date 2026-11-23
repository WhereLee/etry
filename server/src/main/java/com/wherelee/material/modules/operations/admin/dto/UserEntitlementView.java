package com.wherelee.material.modules.operations.admin.dto;

/**
 * 客服视角的用户已购权益（PRD §2 客服：查看用户已购权益）。
 */
public record UserEntitlementView(
        String productType,
        Long productId,
        String title,
        int displayOrder
) {
}