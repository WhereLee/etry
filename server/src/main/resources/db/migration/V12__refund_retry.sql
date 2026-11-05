ALTER TABLE t_refund
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN next_retry_at DATETIME(3) NULL AFTER attempt_count,
    ADD KEY idx_refund_retry (status, next_retry_at);