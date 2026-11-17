package com.wherelee.edu.modules.commerce.api;

/**
 * 履约适配器端口（SPI）。交易核心只表达“发放/撤销某订单商品的权益”这一事实，
 * 具体如何履约由领域模块按 productType 注册实现（课程系列由 course 实现，内部调权益中心）。
 *
 * <p>仿 PaymentGateway 的策略形态：commerce 注入全部实现并按 productType 路由。</p>
 */
public interface FulfillmentHandler {

    String productType();

    void fulfill(FulfillCommand command);

    void revoke(FulfillCommand command);

    record FulfillCommand(Long userId, Long productId, Long orderId, String reason) {
    }
}