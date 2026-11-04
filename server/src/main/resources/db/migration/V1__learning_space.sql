CREATE TABLE t_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='账号';

CREATE TABLE t_series
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT       NOT NULL,
    title      VARCHAR(100) NOT NULL,
    summary    VARCHAR(300) NULL,
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_series_teacher (teacher_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系列';

CREATE TABLE t_series_chapter
(
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    series_id BIGINT       NOT NULL,
    title     VARCHAR(100) NOT NULL,
    sort      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_chapter_series (series_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系列章节';

CREATE TABLE t_series_video
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    chapter_id       BIGINT       NOT NULL,
    title            VARCHAR(100) NOT NULL,
    sort             INT          NOT NULL DEFAULT 0,
    video_key        VARCHAR(255) NOT NULL,
    duration_seconds INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_video_chapter (chapter_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系列视频';

CREATE TABLE t_student_series
(
    id        BIGINT NOT NULL AUTO_INCREMENT,
    user_id   BIGINT NOT NULL,
    series_id BIGINT NOT NULL,
    sort      INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_series (user_id, series_id),
    KEY idx_student_series_user (user_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学生的学习空间';