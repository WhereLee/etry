package com.wherelee.edu.playback;

import com.wherelee.edu.storage.PlaybackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaybackLogRetentionJob {

    private final PlaybackLogRepository repository;
    private final PlaybackProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup() {
        cleanup();
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupDaily() {
        cleanup();
    }

    private void cleanup() {
        int days = Math.max(1, properties.logRetentionDays());
        int deleted = repository.deleteOlderThan(LocalDateTime.now().minusDays(days));
        if (deleted > 0) {
            log.info("播放日志清理完成 retentionDays={} deleted={}", days, deleted);
        }
    }
}