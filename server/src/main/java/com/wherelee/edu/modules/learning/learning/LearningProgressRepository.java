package com.wherelee.edu.modules.learning.learning;

import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LearningProgressRepository {

    private final JdbcTemplate jdbc;
    private final EntitlementQuery entitlementQuery;

    public LearningProgressRepository(JdbcTemplate jdbc, EntitlementQuery entitlementQuery) {
        this.jdbc = jdbc;
        this.entitlementQuery = entitlementQuery;
    }

    public Optional<AccessibleVideo> findAccessibleVideo(Long userId, Long videoId) {
        List<AccessibleVideo> rows = jdbc.query("""
                SELECT v.id AS video_id, c.series_id, v.duration_seconds
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                WHERE v.id = ?
                """, (rs, rowNum) -> new AccessibleVideo(
                rs.getLong("video_id"), rs.getLong("series_id"),
                rs.getInt("duration_seconds")), videoId);
        return rows.stream().findFirst()
                .filter(video -> entitlementQuery.hasActive(userId, video.seriesId(), ProductTypes.COURSE_SERIES));
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
