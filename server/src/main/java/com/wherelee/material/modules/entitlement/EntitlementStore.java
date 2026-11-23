package com.wherelee.material.modules.entitlement;

import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权益中心仓储。
 *
 * <p>t_entitlement 为唯一读写源，t_student_series 退役（表/列保留到回滚窗口过，不再读写）。
 * grant/revoke 语义：唯一键 grant-once、revoke 只撤 ACTIVE 且 source/order 匹配。
 * 商品类型由端口传入（COURSE_SERIES / MATERIAL），仓储不做业务假设。</p>
 */
@Repository
public class EntitlementStore {

    private final JdbcTemplate jdbc;

    public EntitlementStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void grantOrder(Long userId, Long productId, String productType, Long orderId) {
        jdbc.update("""
                INSERT INTO t_entitlement
                    (user_id, product_id, product_type, source, source_order_id, status,
                     display_order, valid_from, granted_at)
                VALUES (?, ?, ?, 'ORDER', ?, 'ACTIVE', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                    status = 'ACTIVE',
                    source_order_id = VALUES(source_order_id),
                    valid_from = COALESCE(valid_from, CURRENT_TIMESTAMP(3)),
                    granted_at = CURRENT_TIMESTAMP(3),
                    revoked_at = NULL,
                    revoke_reason = NULL
                """, userId, productId, productType, orderId);
    }

    public void revokeOrder(Long userId, Long productId, String productType, Long orderId, String reason) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("""
                UPDATE t_entitlement
                SET status = 'REVOKED', revoked_at = ?, revoke_reason = ?
                WHERE user_id = ? AND product_id = ? AND product_type = ?
                  AND source_order_id = ? AND source = 'ORDER' AND status = 'ACTIVE'
                """, now, reason, userId, productId, productType, orderId);
    }

    public void insertManual(Long userId, Long productId, String productType, int displayOrder) {
        jdbc.update("""
                INSERT INTO t_entitlement
                    (user_id, product_id, product_type, source, status, display_order, granted_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, CURRENT_TIMESTAMP(3))
                """, userId, productId, productType, displayOrder);
    }

    public void deleteManual(Long userId, String productType) {
        jdbc.update("""
                DELETE FROM t_entitlement WHERE user_id = ? AND product_type = ? AND source = 'ADMIN'
                """, userId, productType);
    }

    public boolean hasActive(Long userId, Long productId, String productType) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM t_entitlement
                WHERE user_id = ? AND product_id = ? AND product_type = ? AND status = 'ACTIVE'
                  AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP(3))
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                """, Integer.class, userId, productId, productType);
        return count != null && count > 0;
    }

    public Set<Long> findActiveIds(Long userId, String productType, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(productIds.size(), "?"));
        List<Object> args = new ArrayList<>(productIds.size() + 2);
        args.add(userId);
        args.add(productType);
        args.addAll(productIds);
        List<Long> ids = jdbc.query("""
                SELECT DISTINCT product_id
                FROM t_entitlement
                WHERE user_id = ? AND product_type = ? AND status = 'ACTIVE'
                  AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP(3))
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                  AND product_id IN (%s)
                """.formatted(placeholders), (rs, rowNum) -> rs.getLong(1), args.toArray());
        return new HashSet<>(ids);
    }

    public List<EntitlementQuery.ActiveProduct> listActive(Long userId, String productType) {
        return jdbc.query("""
                SELECT product_id, MIN(display_order) AS display_order
                FROM t_entitlement
                WHERE user_id = ? AND product_type = ? AND status = 'ACTIVE'
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                GROUP BY product_id
                ORDER BY display_order, product_id
                """, (rs, rowNum) -> new EntitlementQuery.ActiveProduct(
                rs.getLong("product_id"), productType, rs.getInt("display_order")), userId, productType);
    }

    public List<Long> listManualProductIds(Long userId, String productType) {
        return jdbc.query("""
                SELECT product_id FROM t_entitlement
                WHERE user_id = ? AND product_type = ? AND source = 'ADMIN' AND status = 'ACTIVE'
                ORDER BY display_order, id
                """, (rs, rowNum) -> rs.getLong("product_id"), userId, productType);
    }

    public long countActiveByUser(Long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_entitlement WHERE user_id = ? AND status = 'ACTIVE'
                """, Long.class, userId);
        return count == null ? 0 : count;
    }

    public Map<Long, Long> countActiveByUsers(Collection<Long> userIds, String productType) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids);
        args.add(productType);
        Map<Long, Long> counts = new HashMap<>();
        RowCallbackHandler handler =
                rs -> counts.put(rs.getLong("user_id"), rs.getLong("cnt"));
        jdbc.query("SELECT user_id, COUNT(*) AS cnt FROM t_entitlement"
                        + " WHERE user_id IN (" + placeholders + ") AND product_type = ? AND status = 'ACTIVE'"
                        + " GROUP BY user_id",
                handler, args.toArray());
        return counts;
    }

    public long countActiveByProduct(Long productId, String productType) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT user_id) FROM t_entitlement
                WHERE product_id = ? AND product_type = ? AND status = 'ACTIVE'
                """, Long.class, productId, productType);
        return count == null ? 0 : count;
    }
}