package com.wherelee.edu.modules.platform.resilience;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceMetricsConfig {

    public ResilienceMetricsConfig(LocalRateLimiter localRateLimiter,
                                   LocalFallbackCache localFallbackCache,
                                   MeterRegistry registry) {
        Gauge.builder("infra.redis.local.rate_limit.size", localRateLimiter, LocalRateLimiter::size)
                .description("本地限流窗口数量")
                .register(registry);
        Gauge.builder("infra.redis.local.fallback_cache.size", localFallbackCache, LocalFallbackCache::size)
                .description("本地幂等和告警去重缓存数量")
                .register(registry);
    }
}
