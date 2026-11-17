package com.wherelee.edu.modules.commerce.api;

import java.util.Optional;

/**
 * 订单交易事实读端口。供读模型门面叠加 owned/activeOrder 等交易事实，
 * commerce 只暴露订单事实，不承载商品展示字段。
 */
public interface OrderQuery {

    /** 用户对指定商品的进行中订单（CREATED/PAYING/PAY_FAILED/PAID），无则空。 */
    Optional<ActiveOrderFact> activeOrder(Long userId, Long productId, String productType);

    record ActiveOrderFact(String orderNo, String status) {
    }
}