package com.wherelee.edu.modules.platform.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LocalFallbackCache {

    private final Cache<String, Entry> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public boolean reserve(String key, Duration ttl) {
        AtomicBoolean reserved = new AtomicBoolean(false);
        Instant now = Instant.now();
        cache.asMap().compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                reserved.set(true);
                return new Entry(UUID.randomUUID().toString(), now.plus(ttl));
            }
            return current;
        });
        return reserved.get();
    }

    public void release(String key) {
        cache.invalidate(key);
    }

    public long size() {
        return cache.estimatedSize();
    }

    private record Entry(String token, Instant expiresAt) {
    }
}
