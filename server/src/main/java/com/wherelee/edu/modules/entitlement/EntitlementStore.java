package com.wherelee.edu.modules.entitlement;

import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 权益中心仓储。
 *
 * <p>Phase 1 过渡：t_student_series 仍为权威读写源（语义一比一继承原 commerce.EntitlementRepository
 * 与管理员分配），t_entitlement 同步双写作为影子，供等价对账。Phase 3 切换权威到 t_entitlement
 * 并移除旧表 SQL。旧表 SQL 为课程遗留语义，仅在过渡期存在于本模块内部。</p>
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
                INSERT INTO t_student_series
                    (user_id, series_id, sort, source, status, order_id, valid_from, granted_at)
                VALUES (?, ?, 1, 'ORDER', 'ACTIVE', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                    status = 'ACTIVE',
                    order_id = VALUES(order_id),
                    valid_from = COALESCE(valid_from, CURRENT_TIMESTAMP(3)),
                    granted_at = CURRENT_TIMESTAMP(3),
                    revoked_at = NULL,
                    revoke_reason = NULL
                """, userId, productId, orderId);
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
                UPDATE t_student_series
                SET status = 'REVOKED', revoked_at = ?, revoke_reason = ?
                WHERE user_id = ? AND series_id = ? AND order_id = ?
                  AND source = 'ORDER' AND status = 'ACTIVE'
                """, now, reason, userId, productId, orderId);
        jdbc.update("""
                UPDATE t_entitlement
                SET status = 'REVOKED', revoked_at = ?, revoke_reason = ?
                WHERE user_id = ? AND product_id = ? AND product_type = ?
                  AND source_order_id = ? AND source = 'ORDER' AND status = 'ACTIVE'
                """, now, reason, userId, productId, COURSE, orderId);
    }

    public void insertManual(Long userId, Long productId, int displayOrder) {
        jdbc.update("""
                INSERT INTO t_student_series
                    (user_id, series_id, sort, source, status, granted_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP(3))
                """, userId, productId, displayOrder);
        jdbc.update("""
                INSERT INTO t_entitlement
                    (user_id, product_id, product_type, source, status, display_order, granted_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, CURRENT_TIMESTAMP(3))
                """, userId, productId, COURSE, displayOrder);
    }

    public void deleteManual(Long userId) {
        jdbc.update("DELETE FROM t_student_series WHERE user_id = ? AND source = 'ADMIN'", userId);
        jdbc.update("""
                DELETE FROM t_entitlement WHERE user_id = ? AND product_type = ? AND source = 'ADMIN'
                """, userId, COURSE);
    }

    public boolean hasActive(Long userId, Long productId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM t_student_series
                WHERE user_id = ? AND series_id = ? AND status = 'ACTIVE'
                  AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP(3))
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                """, Integer.class, userId, productId);
        return count != null && count > 0;
    }

    public List<EntitlementQuery.ActiveProduct> listActive(Long userId) {
        return jdbc.query("""
                SELECT series_id, MIN(sort) AS display_order
                FROM t_student_series
                WHERE user_id = ? AND status = 'ACTIVE'
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                GROUP BY series_id
                ORDER BY display_order, series_id
                """, (rs, rowNum) -> new EntitlementQuery.ActiveProduct(
                rs.getLong("series_id"), COURSE, rs.getInt("display_order")), userId);
    }

    public List<Long> listManualProductIds(Long userId) {
        return jdbc.query("""
                SELECT series_id FROM t_student_series
                WHERE user_id = ? AND source = 'ADMIN' AND status = 'ACTIVE'
                ORDER BY sort, id
                """, (rs, rowNum) -> rs.getLong("series_id"), userId);
    }

    public long countActiveByUser(Long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_student_series WHERE user_id = ? AND status = 'ACTIVE'
                """, Long.class, userId);
        return count == null ? 0 : count;
    }

    public long countActiveByProduct(Long productId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT user_id) FROM t_student_series
                WHERE series_id = ? AND status = 'ACTIVE'
                """, Long.class, productId);
        return count == null ? 0 : count;
    }
}