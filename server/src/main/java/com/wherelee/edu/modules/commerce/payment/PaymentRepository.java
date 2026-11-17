package com.wherelee.edu.modules.commerce.payment;

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
            rs.getString("channel_trade_no"),
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
            (Long) rs.getObject("attempt_id"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getString("event_type"),
            rs.getLong("amount_cents"),
            rs.getString("provider_trade_no"),
            rs.getString("provider_notify_id"),
            rs.getString("process_status"),
            rs.getString("error_message"),
            rs.getTimestamp("received_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("processed_at"))
    );

    private static final RowMapper<RefundRecord> REFUND_MAPPER = (rs, rowNum) -> new RefundRecord(
            rs.getLong("id"),
            rs.getString("refund_no"),
            rs.getLong("order_id"),
            (Long) rs.getObject("attempt_id"),
            RefundKind.valueOf(rs.getString("refund_kind")),
            rs.getLong("amount_cents"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            toLocal(rs.getTimestamp("next_retry_at")),
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

    public Optional<PaymentAttemptRecord> findAttemptById(Long attemptId) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt WHERE id = ?
                """, ATTEMPT_MAPPER, attemptId).stream().findFirst();
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

    public Optional<PaymentAttemptRecord> findPaidAttemptByChannelTradeNo(Long orderId, String channelTradeNo) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt
                WHERE order_id = ? AND channel_trade_no = ? AND status = 'SUCCESS'
                ORDER BY id LIMIT 1
                """, ATTEMPT_MAPPER, orderId, channelTradeNo).stream().findFirst();
    }

    public Optional<PaymentAttemptRecord> findLatestSuccessfulAttempt(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt
                WHERE order_id = ? AND status = 'SUCCESS'
                ORDER BY id DESC LIMIT 1
                """, ATTEMPT_MAPPER, orderId).stream().findFirst();
    }

    public List<PaymentAttemptRecord> findSuccessfulAttempts(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt
                WHERE order_id = ? AND status = 'SUCCESS'
                ORDER BY id
                """, ATTEMPT_MAPPER, orderId);
    }

    public List<PaymentAttemptRecord> findAttemptsForReconcile(int limit) {
        return jdbc.query("""
                SELECT * FROM t_payment_attempt
                WHERE status IN ('PROCESSING', 'SUPERSEDED')
                  AND (next_reconcile_at IS NULL OR next_reconcile_at <= CURRENT_TIMESTAMP(3))
                ORDER BY COALESCE(next_reconcile_at, created_at), id
                LIMIT ?
                """, ATTEMPT_MAPPER, limit);
    }

    /**
     * 数据库时间租约，避免多实例/定时任务重复调用同一个支付尝试。
     */
    public int claimAttemptReconcile(Long attemptId, int leaseSeconds) {
        return jdbc.update("""
                UPDATE t_payment_attempt
                SET next_reconcile_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(3)),
                    reconcile_count = reconcile_count + 1
                WHERE id = ?
                  AND status IN ('PROCESSING', 'SUPERSEDED')
                  AND (next_reconcile_at IS NULL OR next_reconcile_at <= CURRENT_TIMESTAMP(3))
                """, Math.max(5, leaseSeconds), attemptId);
    }

    /**
     * 渠道明确关闭成功或交易不存在后落 CLOSED；网络异常保留原状态重试。
     */
    public void markAttemptClosed(Long attemptId) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'CLOSED', finished_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status IN ('PROCESSING', 'SUPERSEDED')
                """, attemptId);
    }

    public void updateProviderState(Long attemptId, String providerStatus, long providerAmountCents,
                                    String scenario, String channelTradeNo) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET provider_status = ?, provider_amount_cents = ?,
                    mock_scenario = COALESCE(?, mock_scenario),
                    channel_trade_no = COALESCE(?, channel_trade_no)
                WHERE id = ?
                """, providerStatus, providerAmountCents, scenario, channelTradeNo, attemptId);
    }

    /**
     * 资金事实优先：允许把 PROCESSING/FAILED/SUPERSEDED/CLOSED 修正为 SUCCESS。
     * 不清除 failure_code/failure_message，旧状态留作审计；SUCCESS 不重复改写。
     */
    public void markAttemptSuccess(Long attemptId, String channelTradeNo) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'SUCCESS', finished_at = CURRENT_TIMESTAMP(3),
                    channel_trade_no = COALESCE(?, channel_trade_no)
                WHERE id = ? AND status IN ('PROCESSING', 'FAILED', 'SUPERSEDED', 'CLOSED')
                """, channelTradeNo, attemptId);
    }

    /**
     * 失败只能写入仍在 PROCESSING/SUPERSEDED 的尝试，不能覆盖 SUCCESS。
     */
    public void markAttemptFailed(Long attemptId, String code, String message) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'FAILED', failure_code = ?, failure_message = ?,
                    finished_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND status IN ('PROCESSING', 'SUPERSEDED')
                """, code, message, attemptId);
    }

    /**
     * 某次尝试成功后，同订单其余仍可支付的尝试让位，等待补偿任务关闭旧收银台。
     */
    public void markOtherProcessingSuperseded(Long orderId, Long succeededAttemptId) {
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'SUPERSEDED', finished_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = ? AND id <> ? AND status = 'PROCESSING'
                """, orderId, succeededAttemptId);
    }

    public boolean insertEvent(String provider, String eventKey, Long attemptId, Long orderId, String orderNo,
                               String eventType, long amountCents, String providerTradeNo,
                               String providerNotifyId, String payloadDigest) {
        return jdbc.update("""
                INSERT IGNORE INTO t_payment_event
                    (provider, event_key, attempt_id, order_id, order_no, event_type,
                     amount_cents, provider_trade_no, provider_notify_id, payload_digest, process_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
                """, provider, eventKey, attemptId, orderId, orderNo, eventType,
                amountCents, providerTradeNo, providerNotifyId, payloadDigest) > 0;
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

    public void markEventManualReview(Long eventId, String message) {
        jdbc.update("""
                UPDATE t_payment_event
                SET process_status = 'MANUAL_REVIEW', processed_at = CURRENT_TIMESTAMP(3),
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
                SELECT * FROM t_refund WHERE order_id = ? AND refund_kind = 'ORDER'
                ORDER BY id LIMIT 1
                """, REFUND_MAPPER, orderId).stream().findFirst();
    }

    public List<RefundRecord> findRefundsByOrder(Long orderId) {
        return jdbc.query("""
                SELECT * FROM t_refund WHERE order_id = ? ORDER BY id
                """, REFUND_MAPPER, orderId);
    }

    public Optional<RefundRecord> findRefundByAttempt(Long attemptId) {
        return jdbc.query("""
                SELECT * FROM t_refund WHERE attempt_id = ? ORDER BY id LIMIT 1
                """, REFUND_MAPPER, attemptId).stream().findFirst();
    }

    public Optional<RefundRecord> findRefundByIdForUpdate(Long refundId) {
        return jdbc.query("""
                SELECT * FROM t_refund WHERE id = ? FOR UPDATE
                """, REFUND_MAPPER, refundId).stream().findFirst();
    }

    public Optional<RefundRecord> findRefundById(Long refundId) {
        return jdbc.query("""
                SELECT * FROM t_refund WHERE id = ?
                """, REFUND_MAPPER, refundId).stream().findFirst();
    }

    public int assignRefundAttempt(Long refundId, Long attemptId) {
        return jdbc.update("""
                UPDATE t_refund SET attempt_id = ? WHERE id = ? AND attempt_id IS NULL
                """, attemptId, refundId);
    }

    public Long createRefund(String refundNo, Long orderId, Long attemptId, RefundKind refundKind,
                             long amountCents, String reason, Long requestedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_refund
                        (refund_no, order_id, attempt_id, refund_kind, amount_cents,
                         status, reason, requested_by)
                    VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, refundNo);
            statement.setLong(2, orderId);
            statement.setLong(3, attemptId);
            statement.setString(4, refundKind.name());
            statement.setLong(5, amountCents);
            statement.setString(6, reason);
            statement.setLong(7, requestedBy);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建退款后未返回 ID");
        }
        return key.longValue();
    }

    /**
     * 退款处理租约。PROCESSING/FAILED 都可能被领取，但同一时间只允许一个 worker。
     */
    public int claimRefund(Long refundId, String leaseToken, int leaseSeconds) {
        return jdbc.update("""
                UPDATE t_refund
                SET status = 'PROCESSING', lease_token = ?, error_message = NULL,
                    next_retry_at = NULL, updated_at = CURRENT_TIMESTAMP(3),
                    lease_until = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(3))
                WHERE id = ?
                  AND status IN ('PROCESSING', 'FAILED')
                  AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3))
                  AND (status = 'PROCESSING'
                       OR (next_retry_at IS NOT NULL AND next_retry_at <= CURRENT_TIMESTAMP(3)))
                """, leaseToken, Math.max(30, leaseSeconds), refundId);
    }

    /**
     * 管理员显式重试时可越过 next_retry_at / 最大次数限制，但仍必须获得租约。
     */
    public int claimRefundManual(Long refundId, String leaseToken, int leaseSeconds) {
        return jdbc.update("""
                UPDATE t_refund
                SET status = 'PROCESSING', lease_token = ?, error_message = NULL,
                    next_retry_at = NULL, updated_at = CURRENT_TIMESTAMP(3),
                    lease_until = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(3))
                WHERE id = ?
                  AND status IN ('PROCESSING', 'FAILED')
                  AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3))
                """, leaseToken, Math.max(30, leaseSeconds), refundId);
    }

    /**
     * 渠道成功是资金事实，可覆盖 FAILED；不要求仍是持有租约的 worker。
     */
    public int markRefundSucceeded(Long refundId, String providerRefundNo) {
        return jdbc.update("""
                UPDATE t_refund
                SET status = 'SUCCEEDED', attempt_count = attempt_count + 1,
                    next_retry_at = NULL, provider_refund_no = ?,
                    succeeded_at = CURRENT_TIMESTAMP(3), error_message = NULL,
                    lease_token = NULL, lease_until = NULL
                WHERE id = ? AND status <> 'SUCCEEDED'
                """, providerRefundNo, refundId);
    }

    public int markRefundFailed(Long refundId, String leaseToken, String message,
                                LocalDateTime nextRetryAt) {
        return jdbc.update("""
                UPDATE t_refund
                SET status = 'FAILED', attempt_count = attempt_count + 1,
                    next_retry_at = ?, error_message = ?,
                    lease_token = NULL, lease_until = NULL
                WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, nextRetryAt == null ? null : Timestamp.valueOf(nextRetryAt), message,
                refundId, leaseToken);
    }

    public List<RefundRecord> findRefundsForReconcile(int olderThanSeconds, int maxAttempts, int limit) {
        return jdbc.query("""
                SELECT * FROM t_refund
                WHERE (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3))
                  AND (
                    (status = 'PROCESSING'
                     AND (lease_until <= CURRENT_TIMESTAMP(3)
                          OR updated_at <= TIMESTAMPADD(SECOND, -?, CURRENT_TIMESTAMP(3))))
                    OR
                    (status = 'FAILED' AND attempt_count < ?
                     AND next_retry_at IS NOT NULL
                     AND next_retry_at <= CURRENT_TIMESTAMP(3))
                  )
                ORDER BY COALESCE(next_retry_at, updated_at), id
                LIMIT ?
                """, REFUND_MAPPER, olderThanSeconds, maxAttempts, limit);
    }

    public long countPaidUnfulfilledBefore(LocalDateTime cutoff) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_order
                WHERE status = 'PAID' AND paid_at <= ?
                """, Long.class, Timestamp.valueOf(cutoff));
        return count == null ? 0 : count;
    }

    public long countPayingBefore(LocalDateTime cutoff) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_order
                WHERE status = 'PAYING' AND updated_at <= ?
                """, Long.class, Timestamp.valueOf(cutoff));
        return count == null ? 0 : count;
    }

    public long countRefundRetryExhausted(int maxAttempts) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_refund
                WHERE status = 'FAILED' AND attempt_count >= ?
                """, Long.class, maxAttempts);
        return count == null ? 0 : count;
    }

    public long countUnrefundedDuplicatePayments() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM t_payment_attempt a
                JOIN t_order o ON o.id = a.order_id
                WHERE a.status = 'SUCCESS'
                  AND a.channel_trade_no IS NOT NULL
                  AND o.provider_trade_no IS NOT NULL
                  AND a.channel_trade_no <> o.provider_trade_no
                  AND NOT EXISTS (
                    SELECT 1 FROM t_refund r
                    WHERE r.attempt_id = a.id AND r.status = 'SUCCEEDED'
                  )
                """, Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 多笔成功支付且存在缺渠道交易号尝试的订单：无法自动判定主支付，
     * 订单退款与重复款退款两条路都会安全拒绝，必须人工核对渠道账单后处置。
     * 典型来源：V15 对多笔 SUCCESS 历史单跳过渠道号回填。
     */
    public long countMultiSuccessMissingChannelTradeNo() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT a.order_id
                    FROM t_payment_attempt a
                    WHERE a.status = 'SUCCESS'
                    GROUP BY a.order_id
                    HAVING COUNT(*) > 1
                       AND SUM(a.channel_trade_no IS NULL OR a.channel_trade_no = '') > 0
                ) ambiguous
                """, Long.class);
        return count == null ? 0 : count;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
