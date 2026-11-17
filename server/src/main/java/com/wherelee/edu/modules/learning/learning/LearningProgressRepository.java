package com.wherelee.edu.modules.learning.learning;

import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import com.wherelee.edu.modules.learning.api.CourseContentQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class LearningProgressRepository {

    private final JdbcTemplate jdbc;
    private final EntitlementQuery entitlementQuery;
    private final CourseContentQuery courseContentQuery;

    public LearningProgressRepository(JdbcTemplate jdbc, EntitlementQuery entitlementQuery,
                                      CourseContentQuery courseContentQuery) {
        this.jdbc = jdbc;
        this.entitlementQuery = entitlementQuery;
        this.courseContentQuery = courseContentQuery;
    }

    public Optional<AccessibleVideo> findAccessibleVideo(Long userId, Long videoId) {
        return courseContentQuery.findVideo(videoId)
                .filter(video -> entitlementQuery.hasActive(userId, video.seriesId(), ProductTypes.COURSE_SERIES))
                .map(video -> new AccessibleVideo(video.videoId(), video.seriesId(), video.durationSeconds()));
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
