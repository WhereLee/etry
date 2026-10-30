-- etry 初始表结构
-- 用户、文件、分享三张表；验证码与各类计数全部走 Redis，不落库

CREATE TABLE users
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    nickname       VARCHAR(20)  NOT NULL,
    bio            VARCHAR(255)          DEFAULT NULL,
    avatar_key     VARCHAR(255)          DEFAULT NULL,
    quota_bytes    BIGINT       NOT NULL DEFAULT 524288000,
    used_bytes     BIGINT       NOT NULL DEFAULT 0,
    reserved_bytes BIGINT       NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL,
    token_version  INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE files
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    object_key       VARCHAR(255) NOT NULL,
    original_name    VARCHAR(255) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    content_type     VARCHAR(100)          DEFAULT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME     NOT NULL,
    committed_at     DATETIME              DEFAULT NULL,
    deleted_at       DATETIME              DEFAULT NULL,
    trash_expires_at DATETIME              DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_files_object_key (object_key),
    KEY idx_files_user_status (user_id, status, deleted_at),
    KEY idx_files_trash (trash_expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE shares
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    file_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    token      VARCHAR(64) NOT NULL,
    code       VARCHAR(100)         DEFAULT NULL,
    expires_at DATETIME    NOT NULL,
    revoked_at DATETIME             DEFAULT NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shares_token (token),
    KEY idx_shares_file (file_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
