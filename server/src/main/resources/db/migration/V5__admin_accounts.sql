ALTER TABLE t_user
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER must_change_password,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER token_version;

CREATE TABLE t_admin_audit_log
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id  BIGINT       NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    target_type  VARCHAR(30)  NOT NULL,
    success      TINYINT(1)   NOT NULL,
    duration_ms  BIGINT       NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_admin_audit_operator_time (operator_id, created_at),
    KEY idx_admin_audit_action_time (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='管理员操作审计';