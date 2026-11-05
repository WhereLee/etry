package com.wherelee.edu.modules.identity.security;

import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.resilience.LocalRateLimiter;
import com.wherelee.edu.modules.platform.resilience.RedisFallbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicRequestThrottleServiceTest {

    @Test
    void blocksAfterConfiguredThreshold() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        PublicRequestThrottleService service =
                new PublicRequestThrottleService(redis, new SimpleMeterRegistry(),
                        new LocalRateLimiter(), new RedisFallbackMetrics(new SimpleMeterRegistry()));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(21L);

        assertThatThrownBy(() -> service.check("invite", "127.0.0.1", 20, 10))
                .isInstanceOf(BizException.class)
                .hasMessage("请求过于频繁，请稍后再试");
    }

    @Test
    void usesLocalFallbackWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        PublicRequestThrottleService service =
                new PublicRequestThrottleService(redis, new SimpleMeterRegistry(),
                        new LocalRateLimiter(), new RedisFallbackMetrics(new SimpleMeterRegistry()));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        for (int i = 0; i < 20; i++) {
            service.check("invite", "127.0.0.1", 20, 10);
        }
        assertThatThrownBy(() -> service.check("invite", "127.0.0.1", 20, 10))
                .isInstanceOf(BizException.class)
                .hasMessage("请求过于频繁，请稍后再试");
    }
}
