ALTER TABLE t_series
    ADD COLUMN sale_status VARCHAR(20) NOT NULL DEFAULT 'OFF_SALE' AFTER status,
    ADD COLUMN price_cents BIGINT NOT NULL DEFAULT 0 AFTER sale_status,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'CNY' AFTER price_cents,
    ADD COLUMN sale_updated_at DATETIME(3) NULL AFTER published_at,
    ADD KEY idx_series_sale (sale_status, status, price_cents);

ALTER TABLE t_student_series
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ADMIN' AFTER series_id,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER source,
    ADD COLUMN order_id BIGINT NULL AFTER status,
    ADD COLUMN valid_from DATETIME(3) NULL AFTER order_id,
    ADD COLUMN valid_to DATETIME(3) NULL AFTER valid_from,
    ADD COLUMN granted_by BIGINT NULL AFTER valid_to,
    ADD COLUMN granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER granted_by,
    ADD COLUMN revoked_at DATETIME(3) NULL AFTER granted_at,
    ADD COLUMN revoke_reason VARCHAR(200) NULL AFTER revoked_at,
    DROP INDEX uk_student_series,
    ADD UNIQUE KEY uk_student_series_source (user_id, series_id, source),
    ADD KEY idx_student_series_active (user_id, status, series_id),
    ADD KEY idx_student_series_order (order_id);

CREATE TABLE t_order
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    order_no               VARCHAR(40)  NOT NULL,
    user_id                BIGINT       NOT NULL,
    series_id              BIGINT       NOT NULL,
    series_title_snapshot  VARCHAR(100) NOT NULL,
    price_cents            BIGINT       NOT NULL,
    currency               VARCHAR(3)   NOT NULL DEFAULT 'CNY',
    status                 VARCHAR(20)  NOT NULL,
    pay_channel            VARCHAR(20)  NULL,
    provider_trade_no      VARCHAR(64)  NULL,
    client_request_id      VARCHAR(64)  NOT NULL,
    active_key             VARCHAR(80)  NULL,
    expire_at              DATETIME(3)  NOT NULL,
    paid_at                DATETIME(3)  NULL,
    fulfilled_at           DATETIME(3)  NULL,
    closed_at              DATETIME(3)  NULL,
    fulfillment_attempts   INT          NOT NULL DEFAULT 0,
    last_error             VARCHAR(500) NULL,
    created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    version                INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_active_key (active_key),
    UNIQUE KEY uk_order_client_request (user_id, client_request_id),
    KEY idx_order_user_created (user_id, created_at),
    KEY idx_order_status_expire (status, expire_at),
    KEY idx_order_status_updated (status, updated_at),
    KEY idx_order_series (series_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='课程订单';

CREATE TABLE t_payment_attempt
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    order_id              BIGINT       NOT NULL,
    attempt_no            INT          NOT NULL,
    channel               VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    provider_trade_no     VARCHAR(64)  NOT NULL,
    provider_status       VARCHAR(20)  NOT NULL,
    provider_amount_cents BIGINT       NOT NULL,
    request_digest        VARCHAR(64)  NULL,
    failure_code          VARCHAR(64)  NULL,
    failure_message       VARCHAR(300) NULL,
    mock_scenario         VARCHAR(40)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    finished_at           DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_attempt_no (order_id, attempt_no),
    UNIQUE KEY uk_payment_provider_trade (channel, provider_trade_no),
    KEY idx_payment_attempt_order (order_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付尝试';

CREATE TABLE t_payment_event
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    provider       VARCHAR(20)  NOT NULL,
    event_key      VARCHAR(120) NOT NULL,
    order_id       BIGINT       NOT NULL,
    order_no       VARCHAR(40)  NOT NULL,
    event_type     VARCHAR(30)  NOT NULL,
    amount_cents   BIGINT       NOT NULL,
    payload_digest VARCHAR(64)  NULL,
    process_status VARCHAR(20)  NOT NULL,
    error_message  VARCHAR(500) NULL,
    received_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at   DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_event_key (provider, event_key),
    KEY idx_payment_event_order (order_id, received_at),
    KEY idx_payment_event_status (process_status, received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付事件';

CREATE TABLE t_refund
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    refund_no          VARCHAR(40)  NOT NULL,
    order_id           BIGINT       NOT NULL,
    amount_cents       BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    provider_refund_no VARCHAR(64)  NULL,
    reason             VARCHAR(200) NOT NULL,
    requested_by       BIGINT       NOT NULL,
    requested_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    succeeded_at       DATETIME(3)  NULL,
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    error_message      VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_refund_order (order_id),
    KEY idx_refund_status (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='退款记录';
