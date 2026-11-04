ALTER TABLE t_series
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' AFTER sort,
    ADD COLUMN published_at DATETIME(3) NULL AFTER status,
    ADD KEY idx_series_teacher_status (teacher_id, status, sort);

UPDATE t_series
SET status = 'PUBLISHED', published_at = created_at;

ALTER TABLE t_series_chapter
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER sort;

ALTER TABLE t_series_video
    MODIFY COLUMN video_key VARCHAR(255) NULL,
    ADD COLUMN media_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER video_key,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER media_status;

UPDATE t_series_video
SET media_status = 'READY'
WHERE video_key IS NOT NULL AND video_key <> '';
