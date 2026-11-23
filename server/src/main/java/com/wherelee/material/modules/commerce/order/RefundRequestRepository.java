package com.wherelee.material.modules.commerce.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RefundRequestRepository {

    public record AdminRow(
            RefundRequestRecord request,
            String orderStatus,
            long priceCents,
            String username,
            String nickname
    ) {
    }

    private static final RowMapper<RefundRequestRecord> MAPPER = (rs, rowNum) -> new RefundRequestRecord(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getLong("user_id"),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getObject("handled_by", Long.class),
            toLocal(rs.getTimestamp("handled_at")),
            rs.getString("handle_note"),
            toLocal(rs.getTimestamp("created_at"))
    );

    private static final RowMapper<AdminRow> ADMIN_MAPPER = (rs, rowNum) -> new AdminRow(
            MAPPER.mapRow(rs, rowNum),
            rs.getString("order_status"),
            rs.getLong("price_cents"),
            rs.getString("username"),
            rs.getString("nickname")
    );

    private final JdbcTemplate jdbc;

    public RefundRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long orderId, String orderNo, long userId, String reason) {
        jdbc.update("""
                INSERT INTO t_refund_request (order_id, order_no, user_id, reason, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, orderId, orderNo, userId, reason);
    }

    public Optional<RefundRequestRecord> findByOrderId(long orderId) {
        return jdbc.query("SELECT * FROM t_refund_request WHERE order_id = ?", MAPPER, orderId)
                .stream().findFirst();
    }

    public Optional<RefundRequestRecord> findById(long id) {
        return jdbc.query("SELECT * FROM t_refund_request WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public int reapply(long orderId, long userId, String reason) {
        return jdbc.update("""
                UPDATE t_refund_request
                SET status = 'PENDING', user_id = ?, reason = ?,
                    handled_by = NULL, handled_at = NULL, handle_note = NULL, created_at = NOW()
                WHERE order_id = ? AND status = 'REJECTED'
                """, userId, reason, orderId);
    }

    public int markHandled(long id, String status, Long operatorId, String note) {
        return jdbc.update("""
                UPDATE t_refund_request
                SET status = ?, handled_by = ?, handled_at = NOW(), handle_note = ?
                WHERE id = ? AND status = 'PENDING'
                """, status, operatorId, note, id);
    }

    public int revertToPending(long id, Long operatorId) {
        return jdbc.update("""
                UPDATE t_refund_request
                SET status = 'PENDING', handled_by = NULL, handled_at = NULL
                WHERE id = ? AND status = 'APPROVED' AND handled_by = ?
                """, id, operatorId);
    }

    public List<AdminRow> adminPage(String status, int limit, long offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.*, o.status AS order_status, o.price_cents, u.username, u.nickname
                FROM t_refund_request r
                JOIN t_order o ON o.id = r.order_id
                JOIN t_user u ON u.id = r.user_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status = ?");
            args.add(status.strip());
        }
        sql.append(" ORDER BY r.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ADMIN_MAPPER, args.toArray());
    }

    public long adminCount(String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM t_refund_request WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.strip());
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}