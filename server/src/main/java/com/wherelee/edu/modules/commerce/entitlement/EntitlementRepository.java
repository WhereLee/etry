package com.wherelee.edu.modules.commerce.entitlement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class EntitlementRepository {

    private final JdbcTemplate jdbc;

    public EntitlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasActive(Long userId, Long seriesId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM t_student_series
                WHERE user_id = ? AND series_id = ? AND status = 'ACTIVE'
                  AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP(3))
                  AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP(3))
                """, Integer.class, userId, seriesId);
        return count != null && count > 0;
    }

    public void grantOrder(Long userId, Long seriesId, Long orderId) {
        jdbc.update("""
                INSERT INTO t_student_series
                    (user_id, series_id, sort, source, status, order_id,
                     valid_from, granted_at)
                VALUES (?, ?, 1, 'ORDER', 'ACTIVE', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                    status = 'ACTIVE',
                    order_id = VALUES(order_id),
                    valid_from = COALESCE(valid_from, CURRENT_TIMESTAMP(3)),
                    granted_at = CURRENT_TIMESTAMP(3),
                    revoked_at = NULL,
                    revoke_reason = NULL
                """, userId, seriesId, orderId);
    }

    public void revokeOrder(Long userId, Long seriesId, Long orderId, String reason) {
        jdbc.update("""
                UPDATE t_student_series
                SET status = 'REVOKED',
                    revoked_at = ?,
                    revoke_reason = ?
                WHERE user_id = ? AND series_id = ? AND order_id = ?
                  AND source = 'ORDER' AND status = 'ACTIVE'
                """, Timestamp.valueOf(LocalDateTime.now()), reason, userId, seriesId, orderId);
    }
}
