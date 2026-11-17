package com.wherelee.edu.modules.course.catalog;

/**
 * 商店目录展示项（课程视角读模型产物）。seriesId 为兼容旧前端的字段名，
 * 值即通用 productId；交易事实 owned/activeOrder 由门面经端口叠加。
 */
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