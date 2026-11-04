package com.wherelee.edu.payment;

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
public class PaymentRepository {

    private static final RowMapper<PaymentAttemptRecord> ATTEMPT_MAPPER = (rs, rowNum) -> new PaymentAttemptRecord(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getInt("attempt_no"),
            rs.getString("channel"),
            rs.getString("status"),
            rs.getString("provider_trade_no"),
            rs.getString("provider_status"),
            rs.getLong("provider_amount_cents"),
            rs.getString("request_digest"),
            rs.getString("failure_code"),
            rs.getString("failure_message"),
            rs.getString("mock_scenario"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("finished_at"))
    );

    private static final RowMapper<PaymentEventRecord> EVENT_MAPPER = (rs, rowNum) -> new PaymentEventRecord(
            rs.getLong("id"),
            rs.getString("provider"),
            rs.getString("event_key"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getString("event_type"),
            rs.getLong("amount_cents"),
            rs.getString("process_status"),
            rs.getString("error_message"),
            rs.getTimestamp("received_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("processed_at"))
    );

    private static final RowMapper<RefundRecord> REFUND_MAPPER = (rs, rowNum) -> new RefundRecord(
            rs.getLong("id"),
            rs.getString("refund_no"),
            rs.getLong("order_id"),
            rs.getLong("amount_cents"),
            rs.getString("status"),
            rs.getString("provider_refund_no"),
            rs.getString("reason"),
            rs.getLong("requested_by"),
            rs.getTimestamp("requested_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("succeeded_at")),
            rs.getString("error_message")
    );

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int nextAttemptNo(Long orderId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_no), 0) + 1 FROM t_payment_attempt WHERE order_id = ?
                """, Integer.class, orderId);
        return value == null ? 1 : value;
    }

    public Long createAttempt(Long orderId, int attemptNo, String channel,
                              String providerTradeNo, long amountCents, String digest) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_payment_attempt
                        (order_id, attempt_no, channel, status, provider_trade_no,
                         provider_status, provider_amount_cents, request_digest)
                    VALUES (?, ?, ?, 'PROCESSING', ?, 'CREATED', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, orderId);
            statement.setInt(2, attemptNo);
            statement.setString(3, channel);
            statement.setString(4, providerTradeNo);
            statement.setLong(5, amountCents);
            statement.setString(6, digest);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建支付尝试后未返回 ID");
        }
        return key.longValue();
    }

    public Optional<PaymentAttemptRecord> findLatestAttempt(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt WHERE order_id = ?
                ORDER BY attempt_no DESC LIMIT 1
                """, ATTEMPT_MAPPER, orderId).stream().findFirst();
    }

    public Optional<PaymentAttemptRecord> findAttemptByProviderTradeNo(String providerTradeNo) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt WHERE provider_trade_no = ?
                ORDER BY id DESC LIMIT 1
                """, ATTEMPT_MAPPER, providerTradeNo).stream().findFirst();
    }

    public List<PaymentAttemptRecord> findAttempts(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt WHERE order_id = ? ORDER BY attempt_no
                """, ATTEMPT_MAPPER, orderId);
    }

    public void updateProviderState(Long attemptId, String providerStatus, long providerAmountCents,
                                    String scenario) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET provider_status = ?, provider_amount_cents = ?,
                    mock_scenario = COALESCE(?, mock_scenario)
                WHERE id = ?
                """, providerStatus, providerAmountCents, scenario, attemptId);
    }

    public void markAttemptSuccess(Long attemptId) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'SUCCESS', finished_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status = 'PROCESSING'
                """, attemptId);
    }

    public void markAttemptFailed(Long attemptId, String code, String message) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'FAILED', failure_code = ?, failure_message = ?,
                    finished_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status = 'PROCESSING'
                """, code, message, attemptId);
    }

    public boolean insertEvent(String provider, String eventKey, Long orderId, String orderNo,
                               String eventType, long amountCents, String payloadDigest) {
        return jdbc.update("""
                INSERT IGNORE INTO t_payment_event
                    (provider, event_key, order_id, order_no, event_type,
                     amount_cents, payload_digest, process_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
                """, provider, eventKey, orderId, orderNo, eventType,
                amountCents, payloadDigest) > 0;
    }

    public Optional<PaymentEventRecord> findEvent(String provider, String eventKey) {
        return jdbc.query("""
                SELECT * FROM t_payment_event WHERE provider = ? AND event_key = ?
                """, EVENT_MAPPER, provider, eventKey).stream().findFirst();
    }

    public void markEventProcessed(Long eventId) {
        jdbc.update("""
                UPDATE t_payment_event
                SET process_status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP(3),
                    error_message = NULL
                WHERE id = ?
                """, eventId);
    }

    public void markEventFailed(Long eventId, String message) {
        jdbc.update("""
                UPDATE t_payment_event
                SET process_status = 'FAILED', processed_at = CURRENT_TIMESTAMP(3),
                    error_message = ?
                WHERE id = ?
                """, message, eventId);
    }

    public List<PaymentEventRecord> findEvents(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_payment_event WHERE order_id = ?
                ORDER BY received_at, id
                """, EVENT_MAPPER, orderId);
    }

    public Optional<RefundRecord> findRefundByOrder(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_refund WHERE order_id = ?
                """, REFUND_MAPPER, orderId).stream().findFirst();
    }

    public Long createRefund(String refundNo, Long orderId, long amountCents,
                             String reason, Long requestedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_refund
                        (refund_no, order_id, amount_cents, status, reason, requested_by)
                    VALUES (?, ?, ?, 'PROCESSING', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, refundNo);
            statement.setLong(2, orderId);
            statement.setLong(3, amountCents);
            statement.setString(4, reason);
            statement.setLong(5, requestedBy);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建退款后未返回 ID");
        }
        return key.longValue();
    }

    public void retryRefund(Long refundId) {
        jdbc.update("""
                UPDATE t_refund
                SET status = 'PROCESSING', error_message = NULL
                WHERE id = ? AND status IN ('FAILED', 'PROCESSING')
                """, refundId);
    }

    public void markRefundSucceeded(Long refundId, String providerRefundNo) {
        jdbc.update("""
                UPDATE t_refund
                SET status = 'SUCCEEDED', provider_refund_no = ?,
                    succeeded_at = CURRENT_TIMESTAMP(3), error_message = NULL
                WHERE id = ? AND status = 'PROCESSING'
                """, providerRefundNo, refundId);
    }

    public void markRefundFailed(Long refundId, String message) {
        jdbc.update("""
                UPDATE t_refund
                SET status = 'FAILED', error_message = ?
                WHERE id = ? AND status = 'PROCESSING'
                """, message, refundId);
    }

    public List<RefundRecord> findPendingRefunds(int olderThanSeconds, int limit) {
        return jdbc.query("""
                SELECT * FROM t_refund
                WHERE status = 'PROCESSING'
                  AND updated_at <= TIMESTAMPADD(SECOND, -?, CURRENT_TIMESTAMP(3))
                ORDER BY updated_at, id
                LIMIT ?
                """, REFUND_MAPPER, olderThanSeconds, limit);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
