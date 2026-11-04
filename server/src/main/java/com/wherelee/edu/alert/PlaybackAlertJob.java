package com.wherelee.edu.alert;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.wherelee.edu.resilience.LocalFallbackCache;
import com.wherelee.edu.resilience.RedisFallbackMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class PlaybackAlertJob {

    private final AlertProperties properties;
    private final AlertLogRepository repository;
    private final AlertNotifier notifier;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final LocalFallbackCache localFallbackCache;
    private final RedisFallbackMetrics fallbackMetrics;

    private double lastDropped = -1;
    private double lastLoginBlocked = -1;

    public PlaybackAlertJob(AlertProperties properties,
                            AlertLogRepository repository,
                            AlertNotifier notifier,
                            StringRedisTemplate redis,
                            MeterRegistry meterRegistry,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            LocalFallbackCache localFallbackCache,
                            RedisFallbackMetrics fallbackMetrics) {
        this.properties = properties;
        this.repository = repository;
        this.notifier = notifier;
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.localFallbackCache = localFallbackCache;
        this.fallbackMetrics = fallbackMetrics;
    }

    @Scheduled(fixedDelayString = "${app.alert.evaluate-interval-ms:300000}",
            initialDelayString = "${app.alert.initial-delay-ms:60000}")
    public void evaluate() {
        if (!properties.enabled()) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(1, properties.windowMinutes()));
        long errors = repository.countEventSince("ERROR", since);
        long stalls = repository.countEventSince("STALLED", since);
        AlertLogRepository.CacheStats cache = repository.cacheStatsSince(since);

        if (errors >= properties.errorThreshold()) {
            emit("PLAYBACK_ERRORS", "HIGH",
                    "最近 " + properties.windowMinutes() + " 分钟播放错误 " + errors + " 次");
        }
        if (stalls >= properties.stallThreshold()) {
            emit("PLAYBACK_STALLS", "MEDIUM",
                    "最近 " + properties.windowMinutes() + " 分钟播放卡顿 " + stalls + " 次");
        }
        if (cache.requests() >= properties.minCacheRequests()
                && (double) cache.misses() / cache.requests() >= properties.cacheMissRateThreshold()) {
            emit("URL_CACHE_MISS", "MEDIUM",
                    "URL 缓存未命中率 " + Math.round((double) cache.misses() / cache.requests() * 100)
                            + "%，请求 " + cache.requests() + " 次");
        }

        Counter droppedCounter = meterRegistry.find("playback.log.dropped").counter();
        double dropped = droppedCounter == null ? 0 : droppedCounter.count();
        if (dropped > 0 && dropped > lastDropped) {
            emit("LOG_QUEUE_DROPPED", "HIGH", "播放日志队列发生丢弃，累计丢弃 " + (long) dropped + " 条");
        }
        lastDropped = Math.max(lastDropped, dropped);

        Counter loginBlockedCounter = meterRegistry.find("security.login.blocked").counter();
        double loginBlocked = loginBlockedCounter == null ? 0 : loginBlockedCounter.count();
        if (loginBlocked > 0 && loginBlocked > lastLoginBlocked) {
            emit("LOGIN_BLOCKED", "MEDIUM", "登录限流触发，累计阻断 " + (long) loginBlocked + " 次");
        }
        lastLoginBlocked = Math.max(lastLoginBlocked, loginBlocked);

        for (CircuitBreaker breaker : circuitBreakerRegistry.getAllCircuitBreakers()) {
            if (breaker.getState() != CircuitBreaker.State.CLOSED) {
                emit("CIRCUIT_OPEN", "HIGH",
                        "熔断器 " + breaker.getName() + " 状态为 " + breaker.getState());
            }
        }
    }

    private void emit(String type, String severity, String message) {
        if (!allowByDedup(type)) {
            return;
        }
        AlertNotifier.DeliveryResult result = notifier.send(message);
        repository.insert(type, severity, message, result.delivered(), result.error());
        log.warn("播放告警 type={} severity={} delivered={} message={}",
                type, severity, result.delivered(), message);
    }

    private boolean allowByDedup(String type) {
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.dedupMinutes()));
        String key = "btry:alert:" + type;
        if (!localFallbackCache.reserve(key, ttl)) {
            return false;
        }
        try {
            Boolean first = redis.opsForValue().setIfAbsent(
                    key, "1", ttl);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            fallbackMetrics.record("alert_dedup", "redis_error");
            log.warn("告警去重 Redis 不可用，切换本地去重 type={} cause={}", type, e.toString());
            return true;
        }
    }
}
