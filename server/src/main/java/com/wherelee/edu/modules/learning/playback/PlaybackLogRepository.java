package com.wherelee.edu.modules.learning.playback;

import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import com.wherelee.edu.modules.learning.api.CourseContentQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class PlaybackLogRepository {

    private final JdbcTemplate jdbc;
    private final EntitlementQuery entitlementQuery;
    private final CourseContentQuery courseContentQuery;

    public PlaybackLogRepository(JdbcTemplate jdbc, EntitlementQuery entitlementQuery,
                                 CourseContentQuery courseContentQuery) {
        this.jdbc = jdbc;
        this.entitlementQuery = entitlementQuery;
        this.courseContentQuery = courseContentQuery;
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
        return courseContentQuery.findVideo(videoId)
                .filter(video -> entitlementQuery.hasActive(userId, video.seriesId(), ProductTypes.COURSE_SERIES))
                .map(video -> new VideoOwnership(video.videoId(), video.seriesId()));
    }

    public record VideoOwnership(Long videoId, Long seriesId) {
    }
}
