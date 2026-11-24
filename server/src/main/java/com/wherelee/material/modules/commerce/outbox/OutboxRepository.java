package com.wherelee.material.modules.commerce.outbox;

import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 发件箱仓储。入队用 INSERT IGNORE 保证幂等生产（同一订单同一类型只一条）；
 * 投递用租约认领（lease_token + lease_until），过期 PROCESSING 可被其他实例重认领，
 * 覆盖 relay 宕机。完成/重试/DEAD 均校验租约，防止过期 worker 误改。
 */
@Repository
public class OutboxRepository {

    private static final String COLUMNS = """
            id, message_type, order_id, order_no, user_id, product_id, product_type, reason,
            status, retry_count, max_retry, lease_token, lease_until, next_retry_at, last_error,
            created_at, updated_at, processed_at
            """;

    private static final RowMapper<OutboxMessage> MAPPER = (rs, rowNum) -> new OutboxMessage(
            rs.getLong("id"),
            rs.getString("message_type"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getLong("user_id"),
            rs.getLong("product_id"),
            rs.getString("product_type"),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            rs.getInt("max_retry"),
            rs.getString("lease_token"),
            toLocal(rs.getTimestamp("lease_until")),
            toLocal(rs.getTimestamp("next_retry_at")),
            rs.getString("last_error"),
            toLocal(rs.getTimestamp("created_at")),
            toLocal(rs.getTimestamp("updated_at")),
            toLocal(rs.getTimestamp("processed_at"))
    );

    private final JdbcTemplate jdbc;
    private final OutboxProperties properties;

    public OutboxRepository(JdbcTemplate jdbc, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public void enqueueFulfill(OrderRecord order) {
        enqueue(OutboxMessage.TYPE_FULFILL, order, null);
    }

    public void enqueueRevoke(OrderRecord order, String reason) {
        enqueue(OutboxMessage.TYPE_REVOKE, order, reason);
    }

    private void enqueue(String messageType, OrderRecord order, String reason) {
        // Phase 2 商品标识仍为 COURSE_SERIES（series 别名），Phase 3 通用化后改读 order 商品字段。
        jdbc.update("""
                INSERT IGNORE INTO t_outbox_message
                    (message_type, order_id, order_no, user_id, product_id, product_type,
                     reason, status, max_retry, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'NEW', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """,
                messageType, order.id(), order.orderNo(), order.userId(), order.seriesId(),
                ProductTypes.COURSE_SERIES, reason, properties.safeMaxRetry());
    }

    /**
     * 租约认领一批可投递消息：NEW/RETRY 到期，或 PROCESSING 租约过期（relay 宕机重认领）。
     */
    @Transactional
    public List<OutboxMessage> claimBatch(String leaseToken, int batchSize, int leaseSeconds) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM t_outbox_message
                WHERE status IN ('NEW', 'RETRY', 'PROCESSING')
                  AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3))
                  AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3))
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> rs.getLong(1), batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids.size() + 2);
        args.add(leaseToken);
        args.add(Math.max(10, leaseSeconds));
        args.addAll(ids);
        jdbc.update("""
                UPDATE t_outbox_message
                SET status = 'PROCESSING', lease_token = ?, retry_count = retry_count + 1,
                    next_retry_at = NULL, last_error = NULL, updated_at = CURRENT_TIMESTAMP(3),
                    lease_until = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(3))
                WHERE id IN (%s) AND status IN ('NEW', 'RETRY', 'PROCESSING')
                """.formatted(placeholders), args.toArray());
        return jdbc.query("SELECT " + COLUMNS + """
                FROM t_outbox_message
                WHERE lease_token = ? AND status = 'PROCESSING'
                ORDER BY id
                """, MAPPER, leaseToken);
    }

    public int markDone(Long id, String leaseToken) {
        return jdbc.update("""
                UPDATE t_outbox_message
                SET status = 'DONE', processed_at = CURRENT_TIMESTAMP(3),
                    lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND lease_token = ? AND status = 'PROCESSING'
                """, id, leaseToken);
    }

    public int markRetry(Long id, String leaseToken, String error, LocalDateTime nextRetryAt) {
        return jdbc.update("""
                UPDATE t_outbox_message
                SET status = 'RETRY', last_error = ?, next_retry_at = ?,
                    lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND lease_token = ? AND status = 'PROCESSING'
                """, error, nextRetryAt == null ? null : Timestamp.valueOf(nextRetryAt), id, leaseToken);
    }

    public int markDead(Long id, String leaseToken, String error) {
        return jdbc.update("""
                UPDATE t_outbox_message
                SET status = 'DEAD', last_error = ?, processed_at = CURRENT_TIMESTAMP(3),
                    lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND lease_token = ? AND status = 'PROCESSING'
                """, error, id, leaseToken);
    }

    public Optional<OutboxMessage> findByOrderAndType(Long orderId, String messageType) {
        return jdbc.query("SELECT " + COLUMNS + """
                FROM t_outbox_message WHERE order_id = ? AND message_type = ?
                """, MAPPER, orderId, messageType).stream().findFirst();
    }

    public long countByStatus(String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_outbox_message WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}