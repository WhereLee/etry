package com.wherelee.edu.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数，防止密码爆破。
 *
 * 用滑动窗口：每次失败都刷新 TTL，含义是「从最后一次失败起 N 分钟内累计错 M 次」。
 * Redis 不可用时放行，不因为缓存故障把所有人挡在门外。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String KEY_PREFIX = "edu:login:fail:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties properties;

    public boolean isLocked(String username) {
        String value = read(key(username));
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= properties.loginFailThreshold();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void recordFailure(String username) {
        String key = key(username);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(properties.loginFailTtlMinutes()));
            }
        } catch (Exception e) {
            log.warn("记录登录失败次数失败 username={}", username, e);
        }
    }

    public void clear(String username) {
        try {
            redisTemplate.delete(key(username));
        } catch (Exception e) {
            log.warn("清除登录失败次数失败 username={}", username, e);
        }
    }

    public long remainingAttempts(String username) {
        String value = read(key(username));
        int used = 0;
        if (value != null) {
            try {
                used = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                used = 0;
            }
        }
        return Math.max(0, properties.loginFailThreshold() - used);
    }

    private String read(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取登录失败次数失败 key={}", key, e);
            return null;
        }
    }

    private String key(String username) {
        return KEY_PREFIX + username;
    }
}
