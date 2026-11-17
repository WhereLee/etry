-- 权益中心：通用权益表，领域无关。Phase 1 与 t_student_series 双写，旧表仍为权威读源；
-- 本迁移只新增表并从旧表回填，不改旧表结构，保证旧 jar 回滚仍可启动。
CREATE TABLE t_entitlement
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    product_type    VARCHAR(40)  NOT NULL,
    source          VARCHAR(20)  NOT NULL,
    source_order_id BIGINT       NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order   INT          NOT NULL DEFAULT 0,
    valid_from      DATETIME(3)  NULL,
    valid_to        DATETIME(3)  NULL,
    granted_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at      DATETIME(3)  NULL,
    revoke_reason   VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entitlement (user_id, product_id, product_type, source),
    KEY idx_entitlement_user_active (user_id, status, product_type),
    KEY idx_entitlement_product (product_id, product_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通用权益中心';

INSERT INTO t_entitlement
    (user_id, product_id, product_type, source, source_order_id, status,
     display_order, valid_from, valid_to, granted_at, revoked_at, revoke_reason)
SELECT user_id, series_id, 'COURSE_SERIES', source, order_id, status,
       sort, valid_from, valid_to, granted_at, revoked_at, revoke_reason
FROM t_student_series;