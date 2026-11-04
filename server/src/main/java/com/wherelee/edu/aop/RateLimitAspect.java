package com.wherelee.edu.aop;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.resilience.LocalRateLimiter;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String LUA =
            "local current = redis.call('incr', KEYS[1]); " +
            "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
            "return current;";

    private final StringRedisTemplate redis;
    private final LocalRateLimiter localRateLimiter;
    private final RedisFallbackMetrics fallbackMetrics;

    public RateLimitAspect(StringRedisTemplate redis,
                           LocalRateLimiter localRateLimiter,
                           RedisFallbackMetrics fallbackMetrics) {
        this.redis = redis;
        this.localRateLimiter = localRateLimiter;
        this.fallbackMetrics = fallbackMetrics;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String userKey = currentUserKey();
        String annotationKey = rateLimit.key().isBlank()
                ? joinPoint.getSignature().toShortString()
                : rateLimit.key();
        String key = "btry:rate:" + annotationKey + ":" + userKey;
        boolean localAllowed;
        try {
            localAllowed = localRateLimiter.allow(key, rateLimit.limit(), rateLimit.windowSeconds());
        } catch (Exception e) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "请求保护暂时不可用，请稍后重试");
        }
        try {
            Long count = redis.execute(
                    new DefaultRedisScript<>(LUA, Long.class),
                    List.of(key),
                    String.valueOf(rateLimit.windowSeconds()));
            if (count != null && count > rateLimit.limit()) {
                throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            fallbackMetrics.record("rate_limit", "redis_error");
            log.warn("Redis 限流不可用，切换本地限流 key={} allowed={} cause={}",
                    key, localAllowed, e.toString());
            if (!localAllowed) {
                throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        }
        return joinPoint.proceed();
    }

    private String currentUserKey() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "anonymous";
            }
            Object userId = attributes.getRequest().getAttribute("userId");
            return userId == null ? "anonymous" : String.valueOf(userId);
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
