package com.wherelee.material.modules.learning.api;

import java.time.LocalDateTime;

/**
 * 播放统计查询端口（learning 拥有 t_playback_log，对外暴露统计读）。
 * 告警等跨切面消费方经此端口取统计，不再直读播放日志表。
 */
public interface PlaybackStatsQuery {

    long countEventSince(String eventType, LocalDateTime since);

    CacheStats cacheStatsSince(LocalDateTime since);

    record CacheStats(long hits, long misses, long requests) {
    }
}