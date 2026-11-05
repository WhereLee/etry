package com.wherelee.edu.modules.platform.alert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class AlertLogRepository {

    private final JdbcTemplate jdbc;

    public AlertLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countEventSince(String eventType, LocalDateTime since) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_playback_log
                WHERE event_type = ? AND created_at >= ?
                """, Long.class, eventType, Timestamp.valueOf(since));
        return value == null ? 0 : value;
    }

    public CacheStats cacheStatsSince(LocalDateTime since) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END), 0) AS hits,
                       COALESCE(SUM(CASE WHEN cache_hit = 0 THEN 1 ELSE 0 END), 0) AS misses,
                       COUNT(*) AS requests
                FROM t_playback_log
                WHERE source = 'BACKEND' AND event_type = 'URL_REQUEST' AND created_at >= ?
                """, (rs, rowNum) -> new CacheStats(
                rs.getLong("hits"), rs.getLong("misses"), rs.getLong("requests")
        ), Timestamp.valueOf(since));
    }

    public void insert(String alertType, String severity, String message,
                       boolean delivered, String deliveryError) {
        jdbc.update("""
                INSERT INTO t_alert_log (alert_type, severity, message, delivered, delivery_error)
                VALUES (?, ?, ?, ?, ?)
                """, alertType, severity, message, delivered, deliveryError);
    }

    public record CacheStats(long hits, long misses, long requests) {
    }
}