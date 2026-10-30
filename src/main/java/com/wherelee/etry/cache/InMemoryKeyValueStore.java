package com.wherelee.etry.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地开发与测试用，进程内存储，重启即清空。
 */
@Component
@ConditionalOnProperty(name = "etry.cache.type", havingValue = "memory")
public class InMemoryKeyValueStore implements KeyValueStore {

    private record Entry(String value, long expireAtMillis) {
        boolean expired(long now) {
            return expireAtMillis > 0 && now > expireAtMillis;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired(System.currentTimeMillis())) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void setEx(String key, String value, Duration ttl) {
        store.put(key, new Entry(value, expireAt(ttl)));
    }

    @Override
    public long incr(String key) {
        AtomicLong result = new AtomicLong();
        store.compute(key, (k, existing) -> {
            long current = 0;
            long expireAt = 0;
            if (existing != null && !existing.expired(System.currentTimeMillis())) {
                current = Long.parseLong(existing.value());
                expireAt = existing.expireAtMillis();
            }
            long next = current + 1;
            result.set(next);
            return new Entry(String.valueOf(next), expireAt);
        });
        return result.get();
    }

    @Override
    public void expire(String key, Duration ttl) {
        Entry entry = store.get(key);
        if (entry != null) {
            store.put(key, new Entry(entry.value(), expireAt(ttl)));
        }
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return get(key) != null;
    }

    private long expireAt(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0;
        }
        return System.currentTimeMillis() + ttl.toMillis();
    }
}
