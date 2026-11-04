package com.wherelee.edu.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final String COLUMNS = """
            id, order_no, user_id, series_id, series_title_snapshot AS series_title,
            price_cents, currency, status, pay_channel, provider_trade_no,
            client_request_id, active_key, expire_at, paid_at, fulfilled_at,
            closed_at, fulfillment_attempts, last_error, created_at, updated_at
            """;

    private static final RowMapper<OrderRecord> MAPPER = (rs, rowNum) -> new OrderRecord(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getLong("user_id"),
            rs.getLong("series_id"),
            rs.getString("series_title"),
            rs.getLong("price_cents"),
            rs.getString("currency"),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getString("pay_channel"),
            rs.getString("provider_trade_no"),
            rs.getString("client_request_id"),
            rs.getString("active_key"),
            rs.getTimestamp("expire_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("paid_at")),
            toLocal(rs.getTimestamp("fulfilled_at")),
            toLocal(rs.getTimestamp("closed_at")),
            rs.getInt("fulfillment_attempts"),
            rs.getString("last_error"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long insert(String orderNo, Long userId, Long seriesId, String seriesTitle,
                       long priceCents, String currency, String clientRequestId,
                       String activeKey, LocalDateTime expireAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_order
                        (order_no, user_id, series_id, series_title_snapshot, price_cents,
                         currency, status, client_request_id, active_key, expire_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'CREATED', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, orderNo);
            statement.setLong(2, userId);
            statement.setLong(3, seriesId);
            statement.setString(4, seriesTitle);
            statement.setLong(5, priceCents);
            statement.setString(6, currency);
            statement.setString(7, clientRequestId);
            statement.setString(8, activeKey);
            statement.setTimestamp(9, Timestamp.valueOf(expireAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建订单后未返回订单 ID");
        }
        return key.longValue();
    }

    public Optional<OrderRecord> findByOrderNo(String orderNo) {
        return jdbc.query("SELECT " + COLUMNS + " FROM t_order WHERE order_no = ?", MAPPER, orderNo)
                .stream().findFirst();
    }

    public Optional<OrderRecord> findById(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM t_order WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<OrderRecord> findOwned(String orderNo, Long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM t_order WHERE order_no = ? AND user_id = ?",
                MAPPER, orderNo, userId).stream().findFirst();
    }

    public Optional<OrderRecord> findByClientRequest(Long userId, String clientRequestId) {
        return jdbc.query("""
                SELECT %s FROM t_order WHERE user_id = ? AND client_request_id = ?
                """.formatted(COLUMNS), MAPPER, userId, clientRequestId).stream().findFirst();
    }

    public Optional<OrderRecord> findActive(Long userId, Long seriesId) {
        return jdbc.query("""
                SELECT %s FROM t_order
                WHERE user_id = ? AND series_id = ?
                  AND status IN ('CREATED', 'PAYING', 'PAY_FAILED', 'PAID')
                ORDER BY id DESC LIMIT 1
                """.formatted(COLUMNS), MAPPER, userId, seriesId).stream().findFirst();
    }

    public List<OrderRecord> findUserOrders(Long userId, int offset, int size) {
        return jdbc.query("""
                SELECT %s FROM t_order
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(COLUMNS), MAPPER, userId, size, offset);
    }

    public long countUserOrders(Long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    public List<OrderRecord> findPendingQuery(int olderThanSeconds, int limit) {
        return jdbc.query("""
                SELECT %s FROM t_order
                WHERE status = 'PAYING'
                  AND updated_at <= TIMESTAMPADD(SECOND, -?, CURRENT_TIMESTAMP(3))
                ORDER BY updated_at, id
                LIMIT ?
                """.formatted(COLUMNS), MAPPER, olderThanSeconds, limit);
    }

    public List<OrderRecord> findPaidUnfulfilled(int limit) {
        return jdbc.query("""
                SELECT %s FROM t_order
                WHERE status = 'PAID'
                ORDER BY paid_at, id
                LIMIT ?
                """.formatted(COLUMNS), MAPPER, limit);
    }

    public List<OrderRecord> findExpired(int limit) {
        return jdbc.query("""
                SELECT %s FROM t_order
                WHERE status IN ('CREATED', 'PAYING', 'PAY_FAILED')
                  AND expire_at <= CURRENT_TIMESTAMP(3)
                ORDER BY expire_at, id
                LIMIT ?
                """.formatted(COLUMNS), MAPPER, limit);
    }

    public List<OrderRecord> findRefunding(int olderThanSeconds, int limit) {
        return jdbc.query("""
                SELECT %s FROM t_order
                WHERE status = 'REFUNDING'
                  AND updated_at <= TIMESTAMPADD(SECOND, -?, CURRENT_TIMESTAMP(3))
                ORDER BY updated_at, id
                LIMIT ?
                """.formatted(COLUMNS), MAPPER, olderThanSeconds, limit);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public int markPaying(String orderNo, String channel) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'PAYING', pay_channel = ?, last_error = NULL,
                    version = version + 1
                WHERE order_no = ? AND status IN ('CREATED', 'PAY_FAILED')
                  AND expire_at > CURRENT_TIMESTAMP(3)
                """, channel, orderNo);
    }

    public int markPayFailed(String orderNo, String message) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'PAY_FAILED', last_error = ?,
                    version = version + 1
                WHERE order_no = ? AND status = 'PAYING'
                """, message, orderNo);
    }

    public int markPaid(String orderNo, String providerTradeNo, String channel) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'PAID', provider_trade_no = ?, pay_channel = ?,
                    paid_at = CURRENT_TIMESTAMP(3), last_error = NULL,
                    version = version + 1
                WHERE order_no = ? AND status = 'PAYING'
                """, providerTradeNo, channel, orderNo);
    }

    public int markFulfilled(String orderNo) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'FULFILLED', fulfilled_at = CURRENT_TIMESTAMP(3),
                    last_error = NULL, active_key = NULL, version = version + 1
                WHERE order_no = ? AND status = 'PAID'
                """, orderNo);
    }

    public int incrementFulfillmentAttempts(String orderNo, String error) {
        return jdbc.update("""
                UPDATE t_order
                SET fulfillment_attempts = fulfillment_attempts + 1,
                    last_error = ?, version = version + 1
                WHERE order_no = ? AND status = 'PAID'
                """, error, orderNo);
    }

    public int close(String orderNo, boolean force) {
        String statusClause = force
                ? "status IN ('CREATED', 'PAY_FAILED')"
                : "status IN ('CREATED', 'PAY_FAILED') AND expire_at <= CURRENT_TIMESTAMP(3)";
        return jdbc.update("""
                UPDATE t_order
                SET status = 'CLOSED', active_key = NULL, closed_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE order_no = ? AND %s
                """.formatted(statusClause), orderNo);
    }

    public int markRefunding(String orderNo) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'REFUNDING', last_error = NULL, version = version + 1
                WHERE order_no = ? AND status IN ('PAID', 'FULFILLED', 'REFUND_FAILED')
                """, orderNo);
    }

    public int markRefunded(String orderNo) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'REFUNDED', last_error = NULL, version = version + 1
                WHERE order_no = ? AND status = 'REFUNDING'
                """, orderNo);
    }

    public int markRefundFailed(String orderNo, String message) {
        return jdbc.update("""
                UPDATE t_order
                SET status = 'REFUND_FAILED', last_error = ?, version = version + 1
                WHERE order_no = ? AND status = 'REFUNDING'
                """, message, orderNo);
    }

    public int markLastError(String orderNo, String message) {
        return jdbc.update("UPDATE t_order SET last_error = ? WHERE order_no = ?", message, orderNo);
    }
}
