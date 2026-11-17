package com.wherelee.edu.modules.commerce.entitlement;

import com.wherelee.edu.modules.commerce.api.FulfillmentHandler;
import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * commerce 侧权益适配门面。
 *
 * <p>Phase 1 解耦后，commerce 不再拥有权益 SQL：hasActive 走权益中心 EntitlementQuery，
 * grantOrder/revokeOrder 走 FulfillmentHandler 端口（course 适配器实现，内部调权益中心）。
 * 本类仅把课程系列标识翻译为通用商品引用（productType=COURSE_SERIES）并路由到端口，
 * 保持 commerce 内部调用方与测试签名不变。Phase 3 商品标识通用化后进一步收敛。</p>
 */
@Repository
public class EntitlementRepository {

    private final EntitlementQuery entitlementQuery;
    private final Map<String, FulfillmentHandler> handlers;

    public EntitlementRepository(EntitlementQuery entitlementQuery, List<FulfillmentHandler> handlerList) {
        this.entitlementQuery = entitlementQuery;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(FulfillmentHandler::productType, Function.identity()));
    }

    public boolean hasActive(Long userId, Long seriesId) {
        return entitlementQuery.hasActive(userId, seriesId, ProductTypes.COURSE_SERIES);
    }

    public void grantOrder(Long userId, Long seriesId, Long orderId) {
        handler().fulfill(new FulfillmentHandler.FulfillCommand(userId, seriesId, orderId, null));
    }

    public void revokeOrder(Long userId, Long seriesId, Long orderId, String reason) {
        handler().revoke(new FulfillmentHandler.FulfillCommand(userId, seriesId, orderId, reason));
    }

    private FulfillmentHandler handler() {
        FulfillmentHandler handler = handlers.get(ProductTypes.COURSE_SERIES);
        if (handler == null) {
            throw new IllegalStateException("缺少 COURSE_SERIES 履约适配器");
        }
        return handler;
    }
}