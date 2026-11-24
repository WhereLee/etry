-- 同一轮对账内，同一业务身份只允许一条差异；NULL 标识通过生成列归一化为 0/空串。
ALTER TABLE t_recon_break
    ADD COLUMN item_key BIGINT GENERATED ALWAYS AS (COALESCE(item_id, 0)) STORED,
    ADD COLUMN order_key BIGINT GENERATED ALWAYS AS (COALESCE(order_id, 0)) STORED,
    ADD COLUMN attempt_key BIGINT GENERATED ALWAYS AS (COALESCE(attempt_id, 0)) STORED,
    ADD COLUMN merchant_key VARCHAR(64) GENERATED ALWAYS AS (COALESCE(merchant_trade_no, '')) STORED,
    ADD COLUMN provider_key VARCHAR(64) GENERATED ALWAYS AS (COALESCE(provider_trade_no, '')) STORED,
    ADD UNIQUE KEY uk_recon_break_identity
        (run_id, break_type, item_key, order_key, attempt_key, merchant_key, provider_key);
