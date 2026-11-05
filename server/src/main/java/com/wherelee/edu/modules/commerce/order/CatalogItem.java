package com.wherelee.edu.modules.commerce.order;

public record CatalogItem(
        Long seriesId,
        String title,
        String summary,
        String teacherName,
        long chapterCount,
        long videoCount,
        long priceCents,
        String currency,
        boolean owned,
        String activeOrderNo,
        String activeOrderStatus
) {
}
