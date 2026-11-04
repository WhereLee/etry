package com.wherelee.edu.playback;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PlaybackLogWriter {

    private final PlaybackLogQueue queue;
    private final PlaybackLogMapper mapper;
    private final Counter persisted;
    private final Counter errors;
    private final CircuitBreaker dbCircuitBreaker;
    private final Retry dbRetry;
    private volatile boolean running = true;
    private Thread thread;

    public PlaybackLogWriter(PlaybackLogQueue queue,
                             PlaybackLogMapper mapper,
                             MeterRegistry registry,
                             CircuitBreakerRegistry circuitBreakerRegistry,
                             RetryRegistry retryRegistry) {
        this.queue = queue;
        this.mapper = mapper;
        this.persisted = Counter.builder("playback.log.persisted").register(registry);
        this.errors = Counter.builder("playback.log.persist.errors").register(registry);
        this.dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("playbackLogDb");
        this.dbRetry = retryRegistry.retry("playbackLogDb");
    }

    @PostConstruct
    public void start() {
        thread = new Thread(this::loop, "playback-log-writer");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        while (running || !queue.isEmpty()) {
            List<PlaybackLogEvent> batch = new ArrayList<>(100);
            try {
                PlaybackLogEvent first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, 99);
                dbRetry.executeRunnable(() ->
                        dbCircuitBreaker.executeRunnable(() -> mapper.insertBatch(batch)));
                persisted.increment(batch.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                errors.increment();
                log.error("批量写入播放日志失败 batchSize={}", batch.size(), e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(3000);
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}