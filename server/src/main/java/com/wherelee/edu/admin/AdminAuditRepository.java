package com.wherelee.edu.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuditRepository {

    private final JdbcTemplate jdbc;

    public AdminAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Long operatorId, String action, String targetType, Long targetId,
                       boolean success, long durationMs, String error, String traceId) {
        jdbc.update("""
                INSERT INTO t_admin_audit_log
                    (operator_id, action, target_type, target_id, success, duration_ms,
                     error_message, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, operatorId, action, targetType, targetId, success, durationMs, error, traceId);
    }
}
