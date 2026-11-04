ALTER TABLE t_playback_log
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER session_id,
    ADD KEY idx_playback_trace (trace_id);

ALTER TABLE t_admin_audit_log
    ADD COLUMN target_id BIGINT NULL AFTER target_type,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER error_message,
    ADD KEY idx_admin_audit_target (target_type, target_id, created_at),
    ADD KEY idx_admin_audit_trace (trace_id);
