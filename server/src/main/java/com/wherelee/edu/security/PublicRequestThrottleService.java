package com.wherelee.edu.security;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.resilience.LocalRateLimiter;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
public class PublicRequestThrottleService {

    private static final String LUA =
            "local current = redis.call('incr', KEYS[1]); " +
            "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
            "return current;";

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;
    private final MeterRegistry registry;
    private final LocalRateLimiter localRateLimiter;
    private final RedisFallbackMetrics fallbackMetrics;

    public PublicRequestThrottleService(StringRedisTemplate redis,
                                        MeterRegistry registry,
                                        LocalRateLimiter localRateLimiter,
                                        RedisFallbackMetrics fallbackMetrics) {
        this.redis = redis;
        this.registry = registry;
        this.localRateLimiter = localRateLimiter;
        this.fallbackMetrics = fallbackMetrics;
    }

    public void check(String scope, String clientIp, int maxAttempts, int windowMinutes) {
        String key = "btry:public:" + scope + ":" + sha256(clientIp == null ? "unknown" : clientIp);
        boolean localAllowed = localRateLimiter.allow(key, maxAttempts, Math.max(1, windowMinutes) * 60);
        try {
            Long count = redis.execute(SCRIPT, List.of(key),
                    String.valueOf(Math.max(1, windowMinutes) * 60));
            if (count != null && count > Math.max(1, maxAttempts)) {
                Counter.builder("security.public.request.blocked")
                        .tag("scope", scope)
                        .register(registry)
                        .increment();
                throw BizException.tooManyRequests("请求过于频繁，请稍后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            fallbackMetrics.record("public_throttle", "redis_error");
            log.warn("公开接口限流 Redis 不可用，切换本地限流 scope={} ip={} allowed={} cause={}",
                    scope, clientIp, localAllowed, e.toString());
            if (!localAllowed) {
                throw BizException.tooManyRequests("请求过于频繁，请稍后再试");
            }
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
}
