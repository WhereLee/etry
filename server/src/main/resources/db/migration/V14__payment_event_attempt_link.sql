-- 支付事件关联具体支付尝试：迟到通知按事件自身的 attempt 处理，不再误标最新尝试。
-- 只新增可空列与索引，不重命名/删除现有列，旧 jar 回滚后仍可启动。
-- 历史事件无法可靠回填 attempt_id，保留 NULL；新事件由代码写入非空 attempt_id。
ALTER TABLE t_payment_event
    ADD COLUMN attempt_id BIGINT NULL AFTER event_key,
    ADD COLUMN provider_trade_no VARCHAR(64) NULL AFTER amount_cents,
    ADD COLUMN provider_notify_id VARCHAR(64) NULL AFTER provider_trade_no,
    ADD KEY idx_payment_event_attempt (attempt_id, received_at);
