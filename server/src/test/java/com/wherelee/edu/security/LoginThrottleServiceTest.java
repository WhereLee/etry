package com.wherelee.edu.security;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.resilience.LocalRateLimiter;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginThrottleServiceTest {

    @Test
    void blocksAttemptsAfterConfiguredThreshold() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityProperties properties = new SecurityProperties(
                List.of("http://localhost:5175"), false, true, 8, 40, 10);
        LoginThrottleService service = new LoginThrottleService(redis, properties, registry,
                new LocalRateLimiter(), new RedisFallbackMetrics(registry));
        when(redis.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redis.opsForValue().get(anyString())).thenReturn("8");

        assertThatThrownBy(() -> service.check("student", "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("登录尝试过于频繁");
        assertThatThrownBy(() -> service.check("student", "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).status())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        org.assertj.core.api.Assertions.assertThat(
                registry.get("security.login.blocked").counter().count()).isEqualTo(2.0);
    }

    @Test
    void usesLocalFallbackWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SecurityProperties properties = new SecurityProperties(
                List.of("http://localhost:5175"), false, true, 8, 40, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoginThrottleService service = new LoginThrottleService(redis, properties, registry,
                new LocalRateLimiter(), new RedisFallbackMetrics(registry));
        when(redis.opsForValue())
                .thenThrow(new RuntimeException("redis down"));

        for (int i = 0; i < 8; i++) {
            service.recordFailure("student", "127.0.0.1");
        }
        assertThatThrownBy(() -> service.check("student", "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("登录尝试过于频繁");
        org.assertj.core.api.Assertions.assertThat(
                registry.get("infra.redis.fallback").tag("component", "login_throttle").counter().count())
                .isGreaterThan(0);
    }

    @Test
    void blocksByIpEvenWhenUsernamesKeepChanging() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("0", "40");
        SecurityProperties properties = new SecurityProperties(
                List.of("http://localhost:5175"), false, true, 8, 40, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoginThrottleService service = new LoginThrottleService(redis, properties, registry,
                new LocalRateLimiter(), new RedisFallbackMetrics(registry));

        assertThatThrownBy(() -> service.check("another-user", "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("登录尝试过于频繁");
    }

    @Test
    void recordsFailureInBothUserAndIpBuckets() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        SecurityProperties properties = new SecurityProperties(
                List.of("http://localhost:5175"), false, true, 8, 40, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoginThrottleService service = new LoginThrottleService(redis, properties, registry,
                new LocalRateLimiter(), new RedisFallbackMetrics(registry));

        service.recordFailure("student", "127.0.0.1");

        verify(redis, times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
