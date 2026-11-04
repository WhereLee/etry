ALTER TABLE t_series_video
    ADD COLUMN original_filename VARCHAR(255) NULL AFTER media_status,
    ADD COLUMN file_size_bytes BIGINT NULL AFTER original_filename,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER file_size_bytes,
    ADD COLUMN pending_object_key VARCHAR(255) NULL AFTER content_type,
    ADD COLUMN uploaded_at DATETIME(3) NULL AFTER pending_object_key;
