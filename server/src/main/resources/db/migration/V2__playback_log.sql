CREATE TABLE t_playback_log
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    video_id         BIGINT       NULL,
    series_id        BIGINT       NULL,
    source           VARCHAR(20)  NOT NULL,
    event_type       VARCHAR(30)  NOT NULL,
    position_seconds INT          NULL,
    duration_seconds INT          NULL,
    cache_hit        TINYINT(1)   NULL,
    message          VARCHAR(500) NULL,
    user_agent       VARCHAR(300) NULL,
    session_id       VARCHAR(64)  NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_playback_user_time (user_id, created_at),
    KEY idx_playback_video_time (video_id, created_at),
    KEY idx_playback_event_time (event_type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='播放行为与错误日志';