package com.wherelee.edu.playback;

import com.wherelee.edu.aop.Measured;
import com.wherelee.edu.common.web.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PlaybackLogService {

    private final PlaybackLogRepository repository;
    private final PlaybackLogQueue queue;

    public PlaybackLogService(PlaybackLogRepository repository, PlaybackLogQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    public void recordUrlRequest(Long userId, Long videoId, Long seriesId,
                                 boolean cacheHit, String userAgent) {
        offer(new PlaybackLogEvent(
                userId, videoId, seriesId, "BACKEND", "URL_REQUEST",
                null, null, cacheHit, null, limit(userAgent, 300), null,
                TraceIdFilter.currentTraceId()));
    }

    @Measured("playback.log.record")
    public void recordFrontend(Long userId, PlaybackEventRequest request, String userAgent) {
        PlaybackLogRepository.VideoOwnership ownership =
                repository.findOwnedVideo(userId, request.videoId()).orElse(null);
        if (ownership == null) {
            log.warn("忽略无权或不存在的视频播放日志 userId={} videoId={}", userId, request.videoId());
            return;
        }
        offer(new PlaybackLogEvent(
                userId, ownership.videoId(), ownership.seriesId(), "FRONTEND",
                request.eventType(), request.positionSeconds(), request.durationSeconds(),
                null, limit(request.message(), 500), limit(userAgent, 300), limit(request.sessionId(), 64),
                TraceIdFilter.currentTraceId()));
    }

    private void offer(PlaybackLogEvent event) {
        if (!queue.offer(event)) {
            log.warn("播放日志队列已满，丢弃事件 source={} type={} userId={}",
                    event.getSource(), event.getEventType(), event.getUserId());
        }
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
