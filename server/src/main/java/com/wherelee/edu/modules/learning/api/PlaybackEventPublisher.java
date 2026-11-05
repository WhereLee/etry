package com.wherelee.edu.modules.learning.api;

/**
 * 播放事件发布端口。课程模块只发布事件，不关心日志队列或持久化实现。
 */
public interface PlaybackEventPublisher {

    void recordUrlRequest(Long userId, Long videoId, Long seriesId,
                          boolean cacheHit, String userAgent);
}