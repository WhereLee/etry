package com.wherelee.edu.modules.commerce.api;

import java.util.List;

/**
 * 商品目录展示端口（读模型）。领域展示字段（讲师/章节/视频数）由商品所属模块提供，
 * commerce 只负责叠加 owned/activeOrder 等交易事实，不直接读商品内容表。
 */
public interface CatalogDisplayQuery {

    String productType();

    List<DisplayProduct> listOnSale();

    record DisplayProduct(Long productId, String title, String summary, String teacherName,
                          long chapterCount, long videoCount, long priceCents, String currency) {
    }
}