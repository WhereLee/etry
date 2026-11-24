package com.wherelee.material.modules.commerce.reconciliation;

import com.wherelee.material.modules.platform.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReconciliationRepository {

    private static final String FILE_COLUMNS = """
            id, provider, statement_date, file_name, content_type, file_sha256, file_size,
            parser_version, row_count, total_amount_cents, status, parse_error, imported_by, created_at
            """;

    private static final RowMapper<ReconciliationModels.StatementFile> FILE_MAPPER = (rs, rowNum) ->
            new ReconciliationModels.StatementFile(
                    rs.getLong("id"), rs.getString("provider"),
                    rs.getDate("statement_date").toLocalDate(), rs.getString("file_name"),
                    rs.getString("content_type"), rs.getString("file_sha256"), rs.getLong("file_size"),
                    rs.getString("parser_version"), rs.getInt("row_count"),
                    rs.getLong("total_amount_cents"), rs.getString("status"),
                    rs.getString("parse_error"), rs.getLong("imported_by"),
                    rs.getTimestamp("created_at").toLocalDateTime());

    private static final RowMapper<ReconciliationModels.StatementRow> STATEMENT_ROW_MAPPER = (rs, rowNum) ->
            new ReconciliationModels.StatementRow(
                    FILE_MAPPER.mapRow(rs, rowNum),
                    (Long) rs.getObject("latest_run_id"), rs.getString("latest_run_status"));

    private static final RowMapper<ReconciliationModels.StatementItem> ITEM_MAPPER = (rs, rowNum) ->
            new ReconciliationModels.StatementItem(
                    rs.getLong("id"), rs.getLong("file_id"), rs.getInt("row_no"),
                    rs.getString("business_type"), rs.getString("merchant_trade_no"),
                    rs.getString("provider_trade_no"), rs.getString("trade_status"),
                    rs.getString("raw_status"), rs.getLong("amount_cents"),
                    (Long) rs.getObject("fee_cents"), rs.getString("currency"), rs.getString("refund_no"),
                    toLocal(rs.getTimestamp("occurred_at")), rs.getString("raw_digest"));

    private static final RowMapper<ReconciliationModels.Run> RUN_MAPPER = (rs, rowNum) ->
            new ReconciliationModels.Run(
                    rs.getLong("id"), rs.getLong("file_id"), rs.getInt("run_version"),
                    rs.getString("status"), rs.getString("rule_version"), rs.getInt("total_rows"),
                    rs.getInt("matched_count"), rs.getInt("break_count"),
                    rs.getInt("unresolved_break_count"), rs.getLong("unmatched_amount_cents"),
                    rs.getString("error_message"), toLocal(rs.getTimestamp("started_at")),
                    toLocal(rs.getTimestamp("finished_at")), rs.getLong("created_by"),
                    rs.getTimestamp("created_at").toLocalDateTime());

    private static final RowMapper<ReconciliationModels.LocalPayment> LOCAL_PAYMENT_MAPPER = (rs, rowNum) ->
            new ReconciliationModels.LocalPayment(
                    rs.getLong("attempt_id"), rs.getLong("order_id"), rs.getString("order_no"),
                    rs.getString("merchant_trade_no"), rs.getString("channel_trade_no"),
                    rs.getString("attempt_status"), rs.getLong("provider_amount_cents"),
                    rs.getString("order_status"), rs.getLong("order_price_cents"),
                    toLocal(rs.getTimestamp("paid_at")));

    private static final RowMapper<ReconciliationModels.Break> BREAK_MAPPER = (rs, rowNum) ->
            new ReconciliationModels.Break(
                    rs.getLong("id"), rs.getLong("run_id"), (Long) rs.getObject("item_id"),
                    rs.getString("break_type"), rs.getString("severity"), rs.getString("status"),
                    (Long) rs.getObject("order_id"), rs.getString("order_no"),
                    (Long) rs.getObject("attempt_id"), rs.getString("provider_trade_no"),
                    rs.getString("merchant_trade_no"), (Long) rs.getObject("local_amount_cents"),
                    (Long) rs.getObject("provider_amount_cents"), rs.getLong("delta_cents"),
                    rs.getString("detail"), rs.getString("resolution_code"),
                    rs.getString("resolution_note"), (Long) rs.getObject("resolved_by"),
                    toLocal(rs.getTimestamp("resolved_at")), rs.getInt("version"),
                    rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public ReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReconciliationModels.StatementFile> findFileByDigest(
            String provider, LocalDate date, String sha256) {
        return jdbc.query("SELECT " + FILE_COLUMNS + " FROM t_recon_statement_file "
                        + "WHERE provider = ? AND statement_date = ? AND file_sha256 = ?",
                FILE_MAPPER, provider, date, sha256).stream().findFirst();
    }

    public Optional<ReconciliationModels.StatementFile> findFileById(Long id) {
        return jdbc.query("SELECT " + FILE_COLUMNS + " FROM t_recon_statement_file WHERE id = ?",
                FILE_MAPPER, id).stream().findFirst();
    }

    public Optional<ReconciliationModels.StatementFile> lockFileForRun(Long id) {
        return jdbc.query("SELECT " + FILE_COLUMNS + " FROM t_recon_statement_file WHERE id = ? FOR UPDATE",
                FILE_MAPPER, id).stream().findFirst();
    }

    public Long createFile(String provider, LocalDate date, String fileName, String contentType,
                           String sha256, long fileSize, byte[] rawContent, String parserVersion,
                           Long importedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO t_recon_statement_file
                        (provider, statement_date, file_name, content_type, file_sha256, file_size,
                         raw_content, parser_version, status, imported_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PARSING', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, provider);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setString(3, fileName);
            ps.setString(4, contentType);
            ps.setString(5, sha256);
            ps.setLong(6, fileSize);
            ps.setBytes(7, rawContent);
            ps.setString(8, parserVersion);
            ps.setLong(9, importedBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建账单文件后未返回 ID");
        }
        return key.longValue();
    }

    public void markFileParsed(Long fileId, int rowCount, long totalAmountCents, String parserVersion) {
        jdbc.update("""
                UPDATE t_recon_statement_file
                SET status = 'PARSED', row_count = ?, total_amount_cents = ?,
                    parser_version = ?, parse_error = NULL
                WHERE id = ?
                """, rowCount, totalAmountCents, parserVersion, fileId);
    }

    public void reopenFile(Long fileId) {
        jdbc.update("""
                UPDATE t_recon_statement_file
                SET status = 'PARSING', parse_error = NULL
                WHERE id = ? AND status = 'FAILED'
                """, fileId);
    }

    public void markFileFailed(Long fileId, String error) {
        jdbc.update("""
                UPDATE t_recon_statement_file
                SET status = 'FAILED', parse_error = ?
                WHERE id = ?
                """, truncate(error, 1000), fileId);
    }

    public void insertItems(Long fileId, List<ParsedBill.Item> items) {
        jdbc.batchUpdate("""
                INSERT INTO t_recon_statement_item
                    (file_id, row_no, business_type, merchant_trade_no, provider_trade_no,
                     trade_status, raw_status, amount_cents, fee_cents, currency, refund_no,
                     occurred_at, raw_digest)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, items, 500, (ps, item) -> {
            ps.setLong(1, fileId);
            ps.setInt(2, item.rowNo());
            ps.setString(3, item.businessType());
            ps.setString(4, item.merchantTradeNo());
            ps.setString(5, item.providerTradeNo());
            ps.setString(6, item.tradeStatus());
            ps.setString(7, item.rawStatus());
            ps.setLong(8, item.amountCents());
            ps.setObject(9, item.feeCents());
            ps.setString(10, item.currency());
            ps.setString(11, item.refundNo());
            ps.setTimestamp(12, item.occurredAt() == null ? null : Timestamp.valueOf(item.occurredAt()));
            ps.setString(13, item.rawDigest());
        });
    }

    public PageResult<ReconciliationModels.StatementRow> findStatements(int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long total = count("SELECT COUNT(*) FROM t_recon_statement_file", new Object[0]);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        List<ReconciliationModels.StatementRow> rows = jdbc.query("""
                SELECT f.*,
                       (SELECT r.id FROM t_recon_run r WHERE r.file_id = f.id
                        ORDER BY r.run_version DESC LIMIT 1) AS latest_run_id,
                       (SELECT r.status FROM t_recon_run r WHERE r.file_id = f.id
                        ORDER BY r.run_version DESC LIMIT 1) AS latest_run_status
                FROM t_recon_statement_file f
                ORDER BY f.statement_date DESC, f.id DESC
                LIMIT ? OFFSET ?
                """, STATEMENT_ROW_MAPPER, safeSize, (safePage - 1) * safeSize);
        return new PageResult<>(rows, total, safePage, safeSize);
    }

    public int nextRunVersion(Long fileId) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(run_version), 0) + 1 FROM t_recon_run WHERE file_id = ?",
                Integer.class, fileId);
        return value == null ? 1 : value;
    }

    public Long createRun(Long fileId, int version, String ruleVersion, Long createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO t_recon_run
                        (file_id, run_version, status, rule_version, created_by)
                    VALUES (?, ?, 'RUNNING', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, fileId);
            ps.setInt(2, version);
            ps.setString(3, ruleVersion);
            ps.setLong(4, createdBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建对账运行后未返回 ID");
        }
        jdbc.update("UPDATE t_recon_run SET started_at = CURRENT_TIMESTAMP(3) WHERE id = ?", key.longValue());
        return key.longValue();
    }

    public void completeRun(Long runId, int totalRows, int matched, int breaks,
                            int unresolved, long unmatchedAmount) {
        jdbc.update("""
                UPDATE t_recon_run
                SET status = 'COMPLETED', total_rows = ?, matched_count = ?, break_count = ?,
                    unresolved_break_count = ?, unmatched_amount_cents = ?,
                    finished_at = CURRENT_TIMESTAMP(3), error_message = NULL
                WHERE id = ?
                """, totalRows, matched, breaks, unresolved, unmatchedAmount, runId);
    }

    public void failRun(Long runId, String error) {
        jdbc.update("""
                UPDATE t_recon_run
                SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, truncate(error, 1000), runId);
    }

    public Optional<ReconciliationModels.Run> findRunById(Long runId) {
        return jdbc.query("SELECT * FROM t_recon_run WHERE id = ?", RUN_MAPPER, runId)
                .stream().findFirst();
    }

    public List<ReconciliationModels.Run> findRunsByFile(Long fileId) {
        return jdbc.query("SELECT * FROM t_recon_run WHERE file_id = ? ORDER BY run_version DESC LIMIT 20",
                RUN_MAPPER, fileId);
    }

    public List<ReconciliationModels.StatementItem> findItems(Long fileId) {
        return jdbc.query("SELECT * FROM t_recon_statement_item WHERE file_id = ? ORDER BY row_no",
                ITEM_MAPPER, fileId);
    }

    public List<ReconciliationModels.LocalPayment> findLocalPayments(Long fileId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        return jdbc.query("""
                SELECT a.id AS attempt_id, a.order_id, o.order_no,
                       a.provider_trade_no AS merchant_trade_no, a.channel_trade_no,
                       a.status AS attempt_status, a.provider_amount_cents,
                       o.status AS order_status, o.price_cents AS order_price_cents, o.paid_at
                FROM t_payment_attempt a
                JOIN t_order o ON o.id = a.order_id
                WHERE EXISTS (
                    SELECT 1 FROM t_recon_statement_item i
                    WHERE i.file_id = ? AND i.merchant_trade_no = a.provider_trade_no
                )
                   OR (a.status = 'SUCCESS' AND o.paid_at >= ? AND o.paid_at < ?)
                ORDER BY a.id
                """, LOCAL_PAYMENT_MAPPER, fileId, Timestamp.valueOf(start),
                Timestamp.valueOf(start.plusDays(1)));
    }

    public void insertMatch(Long runId, Long itemId, Long orderId, String orderNo,
                            Long attemptId, String matchType) {
        jdbc.update("""
                INSERT INTO t_recon_match
                    (run_id, item_id, order_id, order_no, attempt_id, match_type)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE match_type = VALUES(match_type)
                """, runId, itemId, orderId, orderNo, attemptId, matchType);
    }

    public void insertBreak(Long runId, Long itemId, String breakType, String severity,
                            Long orderId, String orderNo, Long attemptId,
                            String providerTradeNo, String merchantTradeNo,
                            Long localAmount, Long providerAmount, long delta, String detail) {
        jdbc.update("""
                INSERT INTO t_recon_break
                    (run_id, item_id, break_type, severity, order_id, order_no, attempt_id,
                     provider_trade_no, merchant_trade_no, local_amount_cents,
                     provider_amount_cents, delta_cents, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    severity = VALUES(severity),
                    local_amount_cents = VALUES(local_amount_cents),
                    provider_amount_cents = VALUES(provider_amount_cents),
                    delta_cents = VALUES(delta_cents),
                    detail = VALUES(detail)
                """, runId, itemId, breakType, severity, orderId, orderNo, attemptId,
                providerTradeNo, merchantTradeNo, localAmount, providerAmount, delta, truncate(detail, 1000));
    }

    public PageResult<ReconciliationModels.Break> findBreaks(Long runId, String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), 100);
        boolean filtered = status != null && !status.isBlank();
        String where = filtered ? " WHERE run_id = ? AND status = ?" : " WHERE run_id = ?";
        Object[] args = filtered ? new Object[]{runId, status} : new Object[]{runId};
        long total = count("SELECT COUNT(*) FROM t_recon_break" + where, args);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        List<Object> queryArgs = new ArrayList<>(List.of(args));
        queryArgs.add(safeSize);
        queryArgs.add((safePage - 1) * safeSize);
        List<ReconciliationModels.Break> rows = jdbc.query(
                "SELECT * FROM t_recon_break" + where
                        + " ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'IN_REVIEW' THEN 1 ELSE 2 END,"
                        + " CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END, id"
                        + " LIMIT ? OFFSET ?", BREAK_MAPPER, queryArgs.toArray());
        return new PageResult<>(rows, total, safePage, safeSize);
    }

    public Optional<ReconciliationModels.Break> findBreakByIdForUpdate(Long breakId) {
        return jdbc.query("SELECT * FROM t_recon_break WHERE id = ? FOR UPDATE", BREAK_MAPPER, breakId)
                .stream().findFirst();
    }

    public int updateBreakStatus(Long breakId, int expectedVersion, String status, String resolutionCode,
                                 String note, Long operatorId) {
        return jdbc.update("""
                UPDATE t_recon_break
                SET status = ?, resolution_code = ?, resolution_note = ?,
                    resolved_by = ?, resolved_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ?
                """, status, resolutionCode, truncate(note, 500), operatorId, breakId, expectedVersion);
    }

    public void insertAction(Long breakId, String action, String from, String to,
                             Long operatorId, String note) {
        jdbc.update("""
                INSERT INTO t_recon_action
                    (break_id, action_type, from_status, to_status, operator_id, note)
                VALUES (?, ?, ?, ?, ?, ?)
                """, breakId, action, from, to, operatorId, truncate(note, 500));
    }

    public long countBreaks(Long runId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM t_recon_break WHERE run_id = ?",
                Long.class, runId);
        return value == null ? 0 : value;
    }

    public long countUnresolvedBreaks(Long runId) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_recon_break
                WHERE run_id = ? AND status IN ('OPEN', 'IN_REVIEW')
                """, Long.class, runId);
        return value == null ? 0 : value;
    }

    public int recoverStuckArtifacts(int timeoutMinutes) {
        int safeMinutes = Math.max(5, timeoutMinutes);
        int files = jdbc.update("""
                UPDATE t_recon_statement_file
                SET status = 'FAILED', parse_error = '解析任务超时，请重新上传账单'
                WHERE status = 'PARSING'
                  AND created_at <= TIMESTAMPADD(MINUTE, -?, CURRENT_TIMESTAMP(3))
                """, safeMinutes);
        int runs = jdbc.update("""
                UPDATE t_recon_run
                SET status = 'FAILED', error_message = '对账任务超时，请重新执行',
                    finished_at = CURRENT_TIMESTAMP(3)
                WHERE status = 'RUNNING'
                  AND started_at <= TIMESTAMPADD(MINUTE, -?, CURRENT_TIMESTAMP(3))
                """, safeMinutes);
        return files + runs;
    }

    public void refreshRunUnresolvedCount(Long runId) {
        jdbc.update("""
                UPDATE t_recon_run r
                SET unresolved_break_count = (
                    SELECT COUNT(*) FROM t_recon_break b
                    WHERE b.run_id = r.id AND b.status IN ('OPEN', 'IN_REVIEW')
                )
                WHERE r.id = ?
                """, runId);
    }

    private long count(String sql, Object[] args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
