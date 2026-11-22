package com.wherelee.material.modules.entitlement.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 权益读端口。hasActive 判定有效权益：status=ACTIVE 且 valid_from/valid_to 在有效期内
 * （NULL 视为不限）。Phase 1 读权威旧表，Phase 3 切到 t_entitlement。
 */
public interface EntitlementQuery {

    boolean hasActive(Long userId, Long productId, String productType);

    /** 用户有效权益列表，按 displayOrder 聚合（同商品多来源取最小序号），供学习空间排序。 */
    List<ActiveProduct> listActive(Long userId, String productType);

    /** 用户 ADMIN 来源的有效商品 ID，按 displayOrder/id 排序，供管理员分配回显。 */
    List<Long> listManualProductIds(Long userId, String productType);

    long countActiveByUser(Long userId);

    /** 批量统计用户 ACTIVE 权益数（按商品类型，不含有效期过滤），供管理台列表叠加。 */
    Map<Long, Long> countActiveByUsers(Collection<Long> userIds, String productType);

    long countActiveByProduct(Long productId, String productType);

    record ActiveProduct(Long productId, String productType, int displayOrder) {
    }
}