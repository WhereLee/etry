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
import java.util.Locale;

@Slf4j
@Service
public class LoginThrottleService {

    private static final String LUA =
            "local current = redis.call('incr', KEYS[1]); " +
            "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
            "return current;";

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;
    private final SecurityProperties properties;
    private final Counter blocked;
    private final LocalRateLimiter localRateLimiter;
    private final RedisFallbackMetrics fallbackMetrics;

    public LoginThrottleService(StringRedisTemplate redis,
                                SecurityProperties properties,
                                MeterRegistry registry,
                                LocalRateLimiter localRateLimiter,
                                RedisFallbackMetrics fallbackMetrics) {
        this.redis = redis;
        this.properties = properties;
        this.blocked = Counter.builder("security.login.blocked").register(registry);
        this.localRateLimiter = localRateLimiter;
        this.fallbackMetrics = fallbackMetrics;
    }

    public void check(String username, String clientIp) {
        if (!properties.loginProtectionEnabled()) {
            return;
        }
        String userKey = key(username, clientIp);
        String addressKey = ipKey(clientIp);
        int windowSeconds = Math.max(1, properties.loginWindowMinutes()) * 60;
        long userCount;
        long ipCount;
        try {
            userCount = count(userKey);
            ipCount = count(addressKey);
        } catch (Exception e) {
            fallbackMetrics.record("login_throttle", "redis_error");
            userCount = localRateLimiter.count(userKey, windowSeconds);
            ipCount = localRateLimiter.count(addressKey, windowSeconds);
            log.warn("登录限流 Redis 不可用，切换本地计数 username={} ip={} userCount={} ipCount={} cause={}",
                    username, clientIp, userCount, ipCount, e.toString());
        }
        if (userCount >= Math.max(1, properties.loginMaxAttempts())
                || ipCount >= Math.max(1, properties.loginIpMaxAttempts())) {
            blocked.increment();
            log.warn("登录尝试超限 username={} ip={} userCount={} ipCount={}",
                    username, clientIp, userCount, ipCount);
            throw new BizException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "登录尝试过于频繁，请稍后再试");
        }
    }

    public void recordFailure(String username, String clientIp) {
        if (!properties.loginProtectionEnabled()) {
            return;
        }
        String userKey = key(username, clientIp);
        String addressKey = ipKey(clientIp);
        int windowSeconds = Math.max(1, properties.loginWindowMinutes()) * 60;
        localRateLimiter.increment(userKey, windowSeconds);
        localRateLimiter.increment(addressKey, windowSeconds);
        try {
            increment(userKey);
            increment(addressKey);
        } catch (Exception e) {
            fallbackMetrics.record("login_throttle", "write");
            log.warn("记录登录失败 Redis 计数失败，本地计数已生效 username={} ip={} cause={}",
                    username, clientIp, e.toString());
        }
    }

    public void clear(String username, String clientIp) {
        if (!properties.loginProtectionEnabled()) {
            return;
        }
        String userKey = key(username, clientIp);
        String addressKey = ipKey(clientIp);
        localRateLimiter.reset(userKey);
        localRateLimiter.reset(addressKey);
        try {
            redis.delete(userKey);
            redis.delete(addressKey);
        } catch (Exception e) {
            fallbackMetrics.record("login_throttle", "clear");
            log.warn("清理登录限流 Redis 键失败，等待 TTL 自动过期 username={} ip={} cause={}",
                    username, clientIp, e.toString());
        }
    }

    String key(String username, String clientIp) {
        String normalized = (username == null ? "" : username.trim().toLowerCase(Locale.ROOT))
                + "|" + (clientIp == null ? "unknown" : clientIp);
        return "btry:login:attempt:" + sha256(normalized);
    }

    String ipKey(String clientIp) {
        return "btry:login:ip:" + sha256(clientIp == null ? "unknown" : clientIp);
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void increment(String key) {
        redis.execute(INCREMENT_SCRIPT, List.of(key),
                String.valueOf(Math.max(1, properties.loginWindowMinutes()) * 60));
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
