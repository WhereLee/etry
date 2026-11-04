package com.wherelee.edu.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class LearningProgressRepository {

    private final JdbcTemplate jdbc;

    public LearningProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccessibleVideo> findAccessibleVideo(Long userId, Long videoId) {
        return jdbc.query("""
                SELECT v.id AS video_id, c.series_id, v.duration_seconds
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                JOIN t_series s ON s.id = c.series_id
                JOIN t_student_series ss ON ss.series_id = s.id AND ss.user_id = ?
                WHERE v.id = ? AND ss.status = 'ACTIVE'
                  AND (ss.valid_to IS NULL OR ss.valid_to > CURRENT_TIMESTAMP(3))
                """, (rs, rowNum) -> new AccessibleVideo(
                rs.getLong("video_id"), rs.getLong("series_id"),
                rs.getInt("duration_seconds")), userId, videoId)
                .stream().findFirst();
    }

    public void upsert(Long userId, Long seriesId, Long videoId,
                       int positionSeconds, int durationSeconds, boolean completed) {
        jdbc.update("""
                INSERT INTO t_learning_progress
                    (user_id, series_id, video_id, position_seconds, duration_seconds, completed)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    series_id = VALUES(series_id),
                    position_seconds = VALUES(position_seconds),
                    duration_seconds = VALUES(duration_seconds),
                    completed = completed OR VALUES(completed)
                """, userId, seriesId, videoId, positionSeconds, durationSeconds, completed);
    }

    public record AccessibleVideo(Long videoId, Long seriesId, int durationSeconds) {
    }
}
