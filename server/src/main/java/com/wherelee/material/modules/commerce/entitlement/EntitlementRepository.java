package com.wherelee.material.modules.commerce.entitlement;

import com.wherelee.material.modules.commerce.api.FulfillmentHandler;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return hasActive(userId, seriesId, ProductTypes.COURSE_SERIES);
    }

    public boolean hasActive(Long userId, Long productId, String productType) {
        return entitlementQuery.hasActive(userId, productId, productType);
    }

    public Set<Long> findActiveIds(Long userId, String productType, Collection<Long> productIds) {
        return entitlementQuery.findActiveIds(userId, productType, productIds);
    }

    public Set<Long> findActiveOrderSourceIds(Collection<Long> orderIds) {
        return entitlementQuery.findActiveOrderSourceIds(orderIds);
    }

    public void grantOrder(Long userId, Long seriesId, Long orderId) {
        grantOrder(userId, seriesId, ProductTypes.COURSE_SERIES, orderId);
    }

    public void grantOrder(Long userId, Long productId, String productType, Long orderId) {
        handler(productType).fulfill(new FulfillmentHandler.FulfillCommand(userId, productId, orderId, null));
    }

    public void revokeOrder(Long userId, Long seriesId, Long orderId, String reason) {
        revokeOrder(userId, seriesId, ProductTypes.COURSE_SERIES, orderId, reason);
    }

    public void revokeOrder(Long userId, Long productId, String productType, Long orderId, String reason) {
        handler(productType).revoke(new FulfillmentHandler.FulfillCommand(userId, productId, orderId, reason));
    }

    private FulfillmentHandler handler(String productType) {
        FulfillmentHandler handler = handlers.get(productType);
        if (handler == null) {
            throw new IllegalStateException("缺少履约适配器：" + productType);
        }
        return handler;
    }
}