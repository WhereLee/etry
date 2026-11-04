package com.wherelee.edu.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LocalRateLimiter {

    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public boolean allow(String key, int limit, int windowSeconds) {
        return increment(key, windowSeconds) <= Math.max(1, limit);
    }

    public long increment(String key, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1, windowSeconds) * 1000L;
        Window window = windows.get(key, ignored -> new Window(now));
        return window.increment(now, windowMillis);
    }

    public long count(String key, int windowSeconds) {
        Window window = windows.getIfPresent(key);
        if (window == null) {
            return 0;
        }
        return window.count(System.currentTimeMillis(), Math.max(1, windowSeconds) * 1000L);
    }

    public void reset(String key) {
        windows.invalidate(key);
    }

    public long size() {
        return windows.estimatedSize();
    }

    private static final class Window {
        private long startedAt;
        private long count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }

        private synchronized long increment(long now, long windowMillis) {
            resetIfExpired(now, windowMillis);
            count += 1;
            return count;
        }

        private synchronized long count(long now, long windowMillis) {
            resetIfExpired(now, windowMillis);
            return count;
        }

        private void resetIfExpired(long now, long windowMillis) {
            if (now - startedAt >= windowMillis) {
                startedAt = now;
                count = 0;
            }
        }
    }
}
