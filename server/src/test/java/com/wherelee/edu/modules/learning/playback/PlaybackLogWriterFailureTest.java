package com.wherelee.edu.modules.learning.playback;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PlaybackLogWriterFailureTest {

    @Test
    void recordsErrorWhenDatabaseWriteFails() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlaybackLogQueue queue = new PlaybackLogQueue(registry);
        PlaybackLogMapper mapper = mock(PlaybackLogMapper.class);
        doThrow(new RuntimeException("db down")).when(mapper).insertBatch(anyList());

        RetryRegistry retryRegistry = RetryRegistry.of(
                RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO).build());
        PlaybackLogWriter writer = new PlaybackLogWriter(
                queue, mapper, registry, CircuitBreakerRegistry.ofDefaults(), retryRegistry);
        writer.start();

        PlaybackLogEvent event = new PlaybackLogEvent();
        event.setSource("TEST");
        event.setEventType("TEST");
        queue.offer(event);
        Thread.sleep(800);
        writer.stop();

        assertThat(registry.get("playback.log.persist.errors").counter().count()).isGreaterThan(0);
    }
}