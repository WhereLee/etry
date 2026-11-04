package com.wherelee.edu.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaybackUrlCacheFailureTest {

    @Test
    void throwsClearFailureWhenCosPresignFails() {
        CosPlaybackService cos = mock(CosPlaybackService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(cos.presignedGetUrl(anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("COS credentials invalid"));

        PlaybackUrlCache cache = new PlaybackUrlCache(
                cos,
                new CosProperties("bucket", "region", 240, 3000, 15000, 2),
                new PlaybackProperties(30, 30),
                redis,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                CircuitBreakerRegistry.ofDefaults(),
                new RedisFallbackMetrics(new SimpleMeterRegistry()));

        assertThatThrownBy(() -> cache.get("video-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COS credentials invalid");
    }
}
