package com.wherelee.material.modules.commerce.order;

import java.time.LocalDateTime;

/**
 * 订单事实。商品引用通用化为 productId/productType/productTitle；
 * seriesId/seriesTitle 为 deprecated 别名（课程系列即 COURSE_SERIES 商品），过渡期保留。
 */
public record OrderRecord(
        Long id,
        String orderNo,
        Long userId,
        Long productId,
        String productType,
        String productTitle,
        long priceCents,
        String currency,
        OrderStatus status,
        String payChannel,
        String providerTradeNo,
        String clientRequestId,
        String activeKey,
        LocalDateTime expireAt,
        LocalDateTime paidAt,
        LocalDateTime fulfilledAt,
        LocalDateTime closedAt,
        int fulfillmentAttempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** deprecated 别名，等价 productId。 */
    public Long seriesId() {
        return productId;
    }

    /** deprecated 别名，等价 productTitle。 */
    public String seriesTitle() {
        return productTitle;
    }

    public boolean expired() {
        return expireAt != null && expireAt.isBefore(LocalDateTime.now());
    }
}