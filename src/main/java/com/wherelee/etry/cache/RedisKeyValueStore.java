package com.wherelee.etry.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "etry.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisKeyValueStore implements KeyValueStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void setEx(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public long incr(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    @Override
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
