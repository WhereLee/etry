package com.wherelee.material.modules.entitlement.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权益写端口。grant/revoke 幂等：唯一键 (user_id, product_id, product_type, source) 保证 grant-once，
 * revoke 只撤 ACTIVE 且来源匹配的权益。
 */
public interface EntitlementCommand {

    /** 订单履约发放，source=ORDER。 */
    void grant(GrantCommand command);

    /** 管理员手动分配，source=ADMIN。 */
    void grantManual(GrantCommand command);

    /** 撤销订单来源权益，source=ORDER，按 sourceOrderId 精确匹配。 */
    void revoke(RevokeCommand command);

    /** 整批替换某用户的 ADMIN 分配（先清 ADMIN 源再按顺序写入），保留 ORDER 源不动。 */
    void replaceManual(Long userId, String productType, List<Long> productIds);

    record GrantCommand(
            Long userId,
            Long productId,
            String productType,
            Long sourceOrderId,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            int displayOrder
    ) {
    }

    record RevokeCommand(
            Long userId,
            Long productId,
            String productType,
            Long sourceOrderId,
            String reason
    ) {
    }
}