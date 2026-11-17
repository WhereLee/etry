-- 回滚兼容：恢复旧 jar「每订单一条退款」的并发防重保护。
-- V15 删除 uk_refund_order 后，旧 jar（插入 attempt_id 为 NULL）失去唯一键保护，
-- 而 uk_refund_attempt 对 NULL 不生效（MySQL 唯一键允许多个 NULL），存在双重退款风险。
-- 生成列仅在 attempt_id 为 NULL（旧式退款）时取 order_id，否则为 NULL：
--   旧式退款：同订单第二条命中唯一键被拦截（等价旧 uk_refund_order）；
--   新式退款：attempt_id 非空，生成列为 NULL，唯一键不约束，多 attempt 退款能力不变。
-- 只增列/键，旧 jar 的 INSERT 不引用生成列，仍可正常启动与写入。
ALTER TABLE t_refund
    ADD COLUMN legacy_order_id BIGINT
        GENERATED ALWAYS AS (IF(attempt_id IS NULL, order_id, NULL)) STORED,
    ADD UNIQUE KEY uk_refund_legacy_order (legacy_order_id);