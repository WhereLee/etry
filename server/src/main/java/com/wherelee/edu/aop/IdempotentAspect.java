package com.wherelee.edu.aop;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.resilience.LocalFallbackCache;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    private final StringRedisTemplate redis;
    private final LocalFallbackCache localFallbackCache;
    private final RedisFallbackMetrics fallbackMetrics;

    public IdempotentAspect(StringRedisTemplate redis,
                            LocalFallbackCache localFallbackCache,
                            RedisFallbackMetrics fallbackMetrics) {
        this.redis = redis;
        this.localFallbackCache = localFallbackCache;
        this.fallbackMetrics = fallbackMetrics;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String keyPart = idempotent.key().isBlank()
                ? joinPoint.getSignature().toShortString()
                : idempotent.key();
        String fingerprint = keyPart + ":" + Arrays.deepToString(joinPoint.getArgs());
        String key = "btry:idempotent:" + sha256(fingerprint);
        Duration ttl = Duration.ofSeconds(Math.max(1, idempotent.windowSeconds()));
        if (!localFallbackCache.reserve(key, ttl)) {
            throw new BizException(HttpStatus.CONFLICT, "重复请求，请勿重复提交");
        }
        boolean redisReserved = false;
        try {
            try {
                Boolean first = redis.opsForValue().setIfAbsent(key, UUID.randomUUID().toString(), ttl);
                if (Boolean.FALSE.equals(first)) {
                    throw new BizException(HttpStatus.CONFLICT, "重复请求，请勿重复提交");
                }
                redisReserved = Boolean.TRUE.equals(first);
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                fallbackMetrics.record("idempotent", "redis_error");
                log.warn("Redis 幂等组件不可用，使用本地幂等缓存 key={} cause={}", key, e.toString());
            }
            return joinPoint.proceed();
        } catch (Throwable e) {
            release(key, redisReserved);
            throw e;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private void release(String key, boolean redisReserved) {
        localFallbackCache.release(key);
        if (!redisReserved) {
            return;
        }
        try {
            redis.delete(key);
        } catch (Exception e) {
            fallbackMetrics.record("idempotent", "release");
            log.warn("释放 Redis 幂等键失败，等待 TTL 自动过期 key={} cause={}", key, e.toString());
        }
    }
}
