package com.wherelee.edu.playback;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PlaybackLogRepository {

    private final JdbcTemplate jdbc;

    public PlaybackLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Long userId, Long videoId, Long seriesId, String source, String eventType,
                       Integer positionSeconds, Integer durationSeconds, Boolean cacheHit,
                       String message, String userAgent, String sessionId) {
        jdbc.update("""
                INSERT INTO t_playback_log
                    (user_id, video_id, series_id, source, event_type, position_seconds,
                     duration_seconds, cache_hit, message, user_agent, session_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, videoId, seriesId, source, eventType, positionSeconds,
                durationSeconds, cacheHit, message, userAgent, sessionId);
    }

    public int deleteOlderThan(LocalDateTime cutoff) {
        return jdbc.update("DELETE FROM t_playback_log WHERE created_at < ?", Timestamp.valueOf(cutoff));
    }

    public Optional<VideoOwnership> findOwnedVideo(Long userId, Long videoId) {
        List<VideoOwnership> rows = jdbc.query("""
                SELECT v.id AS video_id, c.series_id
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                JOIN t_student_series ss ON ss.series_id = c.series_id AND ss.user_id = ?
                 AND ss.status = 'ACTIVE'
                WHERE v.id = ?
                """, (rs, rowNum) -> new VideoOwnership(
                rs.getLong("video_id"),
                rs.getLong("series_id")
        ), userId, videoId);
        return rows.stream().findFirst();
    }

    public record VideoOwnership(Long videoId, Long seriesId) {
    }
}
