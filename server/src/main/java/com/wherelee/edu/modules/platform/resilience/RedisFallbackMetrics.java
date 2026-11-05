package com.wherelee.edu.modules.platform.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RedisFallbackMetrics {

    private final MeterRegistry registry;

    public RedisFallbackMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String component, String operation) {
        Counter.builder("infra.redis.fallback")
                .description("Redis 依赖降级到本地兜底的次数")
                .tag("component", component)
                .tag("operation", operation)
                .register(registry)
                .increment();
    }
}
