package com.wherelee.edu.modules.learning.playback;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class PlaybackLogQueue {

    private static final int CAPACITY = 5000;

    private final BlockingQueue<PlaybackLogEvent> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final Counter dropped;

    public PlaybackLogQueue(MeterRegistry registry) {
        this.dropped = Counter.builder("playback.log.dropped").register(registry);
        Gauge.builder("playback.log.queue.size", queue, value -> value.size()).register(registry);
    }

    public boolean offer(PlaybackLogEvent event) {
        boolean accepted = queue.offer(event);
        if (!accepted) {
            dropped.increment();
        }
        return accepted;
    }

    public PlaybackLogEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int drainTo(List<PlaybackLogEvent> target, int maxElements) {
        return queue.drainTo(target, maxElements);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }
}