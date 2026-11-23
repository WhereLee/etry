package com.wherelee.material.modules.platform.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminAuditRepository {

    public record AuditLogRecord(
            Long id,
            Long operatorId,
            String action,
            String targetType,
            Long targetId,
            boolean success,
            long durationMs,
            String errorMessage,
            String traceId,
            LocalDateTime createdAt
    ) {
    }

    private static final RowMapper<AuditLogRecord> MAPPER = (rs, rowNum) -> new AuditLogRecord(
            rs.getLong("id"),
            rs.getLong("operator_id"),
            rs.getString("action"),
            rs.getString("target_type"),
            rs.getObject("target_id", Long.class),
            rs.getBoolean("success"),
            rs.getLong("duration_ms"),
            rs.getString("error_message"),
            rs.getString("trace_id"),
            toLocal(rs.getTimestamp("created_at"))
    );

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

    public List<AuditLogRecord> page(Long operatorId, String action, String targetType, Boolean success,
                                     int limit, long offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM t_admin_audit_log WHERE 1=1");
        List<Object> args = filters(operatorId, action, targetType, success, sql);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(Long operatorId, String action, String targetType, Boolean success) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM t_admin_audit_log WHERE 1=1");
        List<Object> args = filters(operatorId, action, targetType, success, sql);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private static List<Object> filters(Long operatorId, String action, String targetType, Boolean success,
                                        StringBuilder sql) {
        List<Object> args = new ArrayList<>();
        if (operatorId != null) {
            sql.append(" AND operator_id = ?");
            args.add(operatorId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action.strip());
        }
        if (targetType != null && !targetType.isBlank()) {
            sql.append(" AND target_type = ?");
            args.add(targetType.strip());
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        return args;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
