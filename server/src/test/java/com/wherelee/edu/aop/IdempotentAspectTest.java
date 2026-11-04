package com.wherelee.edu.aop;

import com.wherelee.edu.resilience.LocalFallbackCache;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private ProceedingJoinPoint joinPoint;

    private IdempotentAspect aspect;
    private Idempotent idempotent;

    @BeforeEach
    void setUp() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        aspect = new IdempotentAspect(redis, new LocalFallbackCache(), new RedisFallbackMetrics(registry));
        Method method = getClass().getDeclaredMethod("sample", SecretArgument.class);
        idempotent = method.getAnnotation(Idempotent.class);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void hashesArgumentsSoSecretsNeverEnterRedisKeys() throws Throwable {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new SecretArgument("SuperSecret123")});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, idempotent);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(key.capture(), anyString(), any(Duration.class));
        assertThat(result).isEqualTo("ok");
        assertThat(key.getValue()).startsWith("btry:idempotent:");
        assertThat(key.getValue()).doesNotContain("SuperSecret123");
    }

    @Test
    void releasesReservationWhenBusinessCallFails() throws Throwable {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new SecretArgument("SuperSecret123")});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> aspect.around(joinPoint, idempotent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        verify(redis).delete(anyString());
    }

    @Test
    void localFallbackBlocksDuplicateWhenRedisIsUnavailable() throws Throwable {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{new SecretArgument("SuperSecret123")});
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint, idempotent)).isEqualTo("ok");
        assertThatThrownBy(() -> aspect.around(joinPoint, idempotent))
                .isInstanceOf(com.wherelee.edu.common.BizException.class)
                .hasMessage("重复请求，请勿重复提交");
    }

    @Idempotent(key = "test", windowSeconds = 5)
    void sample(SecretArgument argument) {
    }

    record SecretArgument(String value) {
    }
}
