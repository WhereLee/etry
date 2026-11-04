CREATE TABLE t_learning_progress
(
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    user_id          BIGINT   NOT NULL,
    series_id        BIGINT   NOT NULL,
    video_id         BIGINT   NOT NULL,
    position_seconds INT      NOT NULL DEFAULT 0,
    duration_seconds INT      NOT NULL DEFAULT 0,
    completed        TINYINT(1) NOT NULL DEFAULT 0,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_progress_user_video (user_id, video_id),
    KEY idx_learning_progress_user_series (user_id, series_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学员学习进度';
