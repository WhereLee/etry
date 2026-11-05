package com.wherelee.edu.modules.platform.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.modules.platform.resilience.RedisFallbackMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaybackUrlCacheTest {

    @Test
    void fallsBackToLocalGenerationWhenRedisIsDown() {
        CosPlaybackService cos = mock(CosPlaybackService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(cos.presignedGetUrl(anyString(), any(Duration.class)))
                .thenReturn("https://example.test/video.mp4");

        PlaybackUrlCache cache = new PlaybackUrlCache(
                cos,
                new CosProperties("bucket", "region", 240, 3000, 15000, 2),
                new PlaybackProperties(30, 30),
                redis,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                CircuitBreakerRegistry.ofDefaults(),
                new RedisFallbackMetrics(new SimpleMeterRegistry()));

        PlaybackUrlCache.CachedUrl result = cache.get("video-key");

        assertThat(result.url()).isEqualTo("https://example.test/video.mp4");
        assertThat(result.expiresAt()).isNotNull();
    }
}
