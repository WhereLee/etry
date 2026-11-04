package com.wherelee.edu.playback;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackLogQueueTest {

    @Test
    void reportsDroppedWhenQueueIsFull() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlaybackLogQueue queue = new PlaybackLogQueue(registry);
        PlaybackLogEvent event = new PlaybackLogEvent();
        event.setSource("TEST");
        event.setEventType("TEST");

        for (int i = 0; i < 5000; i++) {
            assertThat(queue.offer(event)).isTrue();
        }
        assertThat(queue.offer(event)).isFalse();
        assertThat(registry.get("playback.log.dropped").counter().count()).isEqualTo(1.0);
    }
}