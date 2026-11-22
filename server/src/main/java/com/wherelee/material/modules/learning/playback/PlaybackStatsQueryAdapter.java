package com.wherelee.material.modules.learning.playback;

import com.wherelee.material.modules.learning.api.PlaybackStatsQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 播放统计查询适配器：只读 learning 自己归属的 t_playback_log。
 */
@Component
public class PlaybackStatsQueryAdapter implements PlaybackStatsQuery {

    private final JdbcTemplate jdbc;

    public PlaybackStatsQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countEventSince(String eventType, LocalDateTime since) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_playback_log
                WHERE event_type = ? AND created_at >= ?
                """, Long.class, eventType, Timestamp.valueOf(since));
        return value == null ? 0 : value;
    }

    @Override
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
}