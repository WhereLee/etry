package com.wherelee.material.modules.commerce.api;

import java.util.Optional;

/**
 * 可售商品目录端口（SPI）。交易核心下单时只通过此端口校验商品可售并取价格快照，
 * 不直接读领域商品表。按 productType 策略注册，课程系列由 course 实现。
 *
 * <p>领域展示字段（教师/章节数等）不进此端口，由读模型门面另行组装（Phase 4）。</p>
 */
public interface SellableCatalog {

    String productType();

    Optional<SellableProduct> findPurchasable(Long productId);

    record SellableProduct(Long productId, String productType, String title, long priceCents, String currency) {
    }
}