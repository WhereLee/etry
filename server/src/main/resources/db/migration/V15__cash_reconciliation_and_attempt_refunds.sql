-- Attempt 级资金对账：支付尝试保存真实渠道交易号，并用 next_reconcile_at 做查询节流。
ALTER TABLE t_payment_attempt
    ADD COLUMN channel_trade_no VARCHAR(64) NULL AFTER provider_trade_no,
    ADD COLUMN next_reconcile_at DATETIME(3) NULL AFTER failure_message,
    ADD COLUMN reconcile_count INT NOT NULL DEFAULT 0 AFTER next_reconcile_at,
    ADD KEY idx_payment_attempt_reconcile (status, next_reconcile_at, id);

-- 历史成功尝试只在“该订单只有一个成功尝试”时回填，避免把重复付款串到同一笔 trade_no。
UPDATE t_payment_attempt a
    JOIN (
        SELECT order_id, MIN(id) AS attempt_id
        FROM t_payment_attempt
        WHERE status = 'SUCCESS'
        GROUP BY order_id
        HAVING COUNT(*) = 1
    ) success_link ON success_link.attempt_id = a.id
    JOIN t_order o ON o.id = a.order_id
SET a.channel_trade_no = o.provider_trade_no
WHERE o.provider_trade_no IS NOT NULL
  AND o.provider_trade_no <> '';

-- 退款从“每订单一条”升级为“每次支付尝试一条”：
-- ORDER 退款会撤销订单权益；DUPLICATE_PAYMENT 只退第二笔资金，不改订单权益。
ALTER TABLE t_refund
    ADD COLUMN attempt_id BIGINT NULL AFTER order_id,
    ADD COLUMN refund_kind VARCHAR(30) NOT NULL DEFAULT 'ORDER' AFTER attempt_id,
    ADD COLUMN lease_token VARCHAR(64) NULL AFTER next_retry_at,
    ADD COLUMN lease_until DATETIME(3) NULL AFTER lease_token,
    DROP INDEX uk_refund_order,
    ADD UNIQUE KEY uk_refund_attempt (attempt_id),
    ADD KEY idx_refund_processing (status, lease_until, next_retry_at, id);

UPDATE t_refund r
    JOIN (
        SELECT order_id, MIN(id) AS attempt_id
        FROM t_payment_attempt
        WHERE status = 'SUCCESS'
        GROUP BY order_id
    ) paid_attempt ON paid_attempt.order_id = r.order_id
SET r.attempt_id = paid_attempt.attempt_id
WHERE r.attempt_id IS NULL;
