-- 渠道账单对账：账单文件、标准化流水、对账运行、匹配关系、差异和人工动作。
CREATE TABLE t_recon_statement_file
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    provider           VARCHAR(20)  NOT NULL,
    statement_date     DATE         NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NULL,
    file_sha256        CHAR(64)     NOT NULL,
    file_size          BIGINT       NOT NULL,
    raw_content        LONGBLOB     NOT NULL,
    content_encoding   VARCHAR(20)  NOT NULL DEFAULT 'gzip',
    parser_version     VARCHAR(30)  NOT NULL,
    row_count          INT          NOT NULL DEFAULT 0,
    total_amount_cents BIGINT       NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL,
    parse_error        VARCHAR(1000) NULL,
    imported_by        BIGINT       NOT NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_file_digest (provider, statement_date, file_sha256),
    KEY idx_recon_file_date (statement_date, provider, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='渠道账单文件';

CREATE TABLE t_recon_statement_item
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    file_id           BIGINT       NOT NULL,
    row_no            INT          NOT NULL,
    business_type     VARCHAR(20)  NOT NULL,
    merchant_trade_no VARCHAR(64)  NULL,
    provider_trade_no VARCHAR(64)  NULL,
    trade_status      VARCHAR(20)  NOT NULL,
    raw_status        VARCHAR(60)  NULL,
    amount_cents      BIGINT       NOT NULL,
    fee_cents         BIGINT       NULL,
    currency          VARCHAR(3)   NOT NULL DEFAULT 'CNY',
    refund_no         VARCHAR(64)  NULL,
    occurred_at       DATETIME(3)  NULL,
    raw_digest        CHAR(64)     NOT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_item_row (file_id, row_no),
    KEY idx_recon_item_merchant (file_id, merchant_trade_no),
    KEY idx_recon_item_provider (file_id, provider_trade_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='标准化渠道账单流水';

CREATE TABLE t_recon_run
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    file_id                BIGINT       NOT NULL,
    run_version            INT          NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    rule_version           VARCHAR(30)  NOT NULL,
    total_rows             INT          NOT NULL DEFAULT 0,
    matched_count          INT          NOT NULL DEFAULT 0,
    break_count            INT          NOT NULL DEFAULT 0,
    unresolved_break_count INT          NOT NULL DEFAULT 0,
    unmatched_amount_cents BIGINT       NOT NULL DEFAULT 0,
    error_message          VARCHAR(1000) NULL,
    started_at             DATETIME(3)  NULL,
    finished_at            DATETIME(3)  NULL,
    created_by             BIGINT       NOT NULL,
    created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_run_version (file_id, run_version),
    KEY idx_recon_run_file (file_id, id),
    KEY idx_recon_run_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='渠道对账运行';

CREATE TABLE t_recon_match
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    run_id      BIGINT      NOT NULL,
    item_id     BIGINT      NOT NULL,
    order_id    BIGINT      NULL,
    order_no    VARCHAR(40) NULL,
    attempt_id  BIGINT      NULL,
    match_type  VARCHAR(30) NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_match_item (run_id, item_id),
    KEY idx_recon_match_order (run_id, order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='渠道流水与本地支付匹配';

CREATE TABLE t_recon_break
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    run_id               BIGINT        NOT NULL,
    item_id              BIGINT        NULL,
    break_type           VARCHAR(40)   NOT NULL,
    severity             VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    order_id              BIGINT        NULL,
    order_no             VARCHAR(40)   NULL,
    attempt_id            BIGINT        NULL,
    provider_trade_no    VARCHAR(64)   NULL,
    merchant_trade_no    VARCHAR(64)   NULL,
    local_amount_cents   BIGINT        NULL,
    provider_amount_cents BIGINT       NULL,
    delta_cents          BIGINT        NOT NULL DEFAULT 0,
    detail               VARCHAR(1000) NOT NULL,
    resolution_code      VARCHAR(40)   NULL,
    resolution_note      VARCHAR(500)  NULL,
    resolved_by          BIGINT        NULL,
    resolved_at          DATETIME(3)   NULL,
    version              INT           NOT NULL DEFAULT 0,
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_recon_break_run_status (run_id, status, break_type),
    KEY idx_recon_break_order (order_no, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='渠道对账差异';

CREATE TABLE t_recon_action
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    break_id    BIGINT       NOT NULL,
    action_type VARCHAR(30)  NOT NULL,
    from_status VARCHAR(20)  NOT NULL,
    to_status   VARCHAR(20)  NOT NULL,
    operator_id BIGINT       NOT NULL,
    note        VARCHAR(500) NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_recon_action_break (break_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账差异处理动作';
