package com.wherelee.edu.modules.entitlement;

import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权益中心仓储。
 *
 * <p>Phase 3 权威翻转：t_entitlement 为唯一读写源，t_student_series 退役（表/列保留到回滚窗口过，
 * 不再读写）。grant/revoke 语义一比一继承：唯一键 grant-once、revoke 只撤 ACTIVE 且 source/order 匹配。
 * 商品类型暂固定 COURSE_SERIES，商品标识通用化后由端口传入。</p>
 */
@Repository
public class EntitlementStore {

    private static final String COURSE = "COURSE_SERIES";

    private final JdbcTemplate jdbc;

    public EntitlementStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void grantOrder(Long userId, Long productId, Long orderId) {
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
                """, userId, productId, COURSE, orderId);
    }

    public void revokeOrder(Long userId, Long productId, Long orderId, String reason) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("""
                UPDATE t_entitlement
                SET status = 'REVOKED', revoked_at = ?, revoke_reason = ?
                WHERE user_id = ? AND product_id = ? AND product_type = ?
                  AND source_order_id = ? AND source = 'ORDER' AND status = 'ACTIVE'
                """, now, reason, userId, productId, COURSE, orderId);
    }

    public void insertManual(Long userId, Long productId, int displayOrder) {
        jdbc.update("""
                INSERT INTO t_entitlement
                    (user_id, product_id, product_type, source, status, display_order, granted_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, CURRENT_TIMESTAMP(3))
                """, userId, productId, COURSE, displayOrder);
    }

    public void deleteManual(Long userId) {
        jdbc.update("""
                DELETE FROM t_entitlement WHERE user_id = ? AND product_type = ? AND source = 'ADMIN'
                """, userId, COURSE);
    }

    public boolean hasActive(Long userId, Long productId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM t_entitlement
                WHERE user_id = ? AND product_id = ? AND product_type = ? AND status = 'ACTIVE'
                  AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP(3))
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                """, Integer.class, userId, productId, COURSE);
        return count != null && count > 0;
    }

    public List<EntitlementQuery.ActiveProduct> listActive(Long userId) {
        return jdbc.query("""
                SELECT product_id, MIN(display_order) AS display_order
                FROM t_entitlement
                WHERE user_id = ? AND product_type = ? AND status = 'ACTIVE'
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                GROUP BY product_id
                ORDER BY display_order, product_id
                """, (rs, rowNum) -> new EntitlementQuery.ActiveProduct(
                rs.getLong("product_id"), COURSE, rs.getInt("display_order")), userId, COURSE);
    }

    public List<Long> listManualProductIds(Long userId) {
        return jdbc.query("""
                SELECT product_id FROM t_entitlement
                WHERE user_id = ? AND product_type = ? AND source = 'ADMIN' AND status = 'ACTIVE'
                ORDER BY display_order, id
                """, (rs, rowNum) -> rs.getLong("product_id"), userId, COURSE);
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

    public long countActiveByProduct(Long productId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT user_id) FROM t_entitlement
                WHERE product_id = ? AND product_type = ? AND status = 'ACTIVE'
                """, Long.class, productId, COURSE);
        return count == null ? 0 : count;
    }
}