package com.wherelee.material.modules.material;

import java.time.LocalDateTime;

/**
 * 电子资料目录事实。saleStatus：ON_SALE/OFF_SALE；fileKey 为空表示尚未挂文件，不可售。
 */
public record MaterialRecord(
        Long id,
        String title,
        String category,
        String stage,
        String subject,
        String summary,
        long priceCents,
        String currency,
        int pageCount,
        int previewPages,
        String coverKey,
        String fileKey,
        String saleStatus,
        LocalDateTime saleUpdatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static final String ON_SALE = "ON_SALE";
    public static final String OFF_SALE = "OFF_SALE";
}