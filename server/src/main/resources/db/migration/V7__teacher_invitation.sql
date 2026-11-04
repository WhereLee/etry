ALTER TABLE t_series
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

CREATE TABLE t_teacher_invitation
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  CHAR(64)     NOT NULL,
    username    VARCHAR(20)  NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at  DATETIME(3)  NOT NULL,
    created_by  BIGINT       NOT NULL,
    used_by     BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    used_at     DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_invitation_token (token_hash),
    KEY idx_teacher_invitation_status (status, expires_at),
    KEY idx_teacher_invitation_creator (created_by, created_at),
    KEY idx_teacher_invitation_username (username, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='教师邀请';
