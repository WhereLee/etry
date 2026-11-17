-- 事务性发件箱（Phase 2）：资金确认/退款事务内持久化履约/撤销意图，
-- relay 租约认领异步幂等投递，重试耗尽转 DEAD 告警。与资金变更同事务，杜绝双写丢失。
CREATE TABLE t_outbox_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_type VARCHAR(20) NOT NULL COMMENT 'FULFILL 履约 / REVOKE 撤销',
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_type VARCHAR(40) NOT NULL,
    reason VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/PROCESSING/RETRY/DONE/DEAD',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 5,
    lease_token VARCHAR(64) NULL,
    lease_until DATETIME(3) NULL,
    next_retry_at DATETIME(3) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_order_type (order_id, message_type),
    KEY idx_outbox_dispatch (status, next_retry_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务性发件箱';