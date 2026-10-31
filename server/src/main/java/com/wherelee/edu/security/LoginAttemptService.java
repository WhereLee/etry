package com.wherelee.edu.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败计数，防止密码爆破。
 *
 * 两个维度：
 *   账号 + IP —— 同一个来源对同一个账号连续试错，锁这个组合（不影响别人登录该账号）
 *   纯 IP    —— 同一来源累计错太多次，锁整个 IP（防止换用户名继续猜）
 *
 * 早期只按账号计数，结果是谁都能把别人账号锁掉：输错 5 次就能让对方 15 分钟登不上。
 *
 * Redis 不可用时放行：不因为缓存故障把所有人挡在门外。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String USER_KEY_PREFIX = "edu:login:fail:";
    private static final String IP_KEY_PREFIX = "edu:login:ip:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties properties;

    /** 登录前判断：IP 被锁，或这个来源对这个账号的失败次数已达阈值 */
    public boolean isLocked(String username, String ip) {
        if (StringUtils.hasText(ip) && exceeds(IP_KEY_PREFIX + ip, properties.ipFailThreshold())) {
            log.warn("登录被锁 ip={}", ip);
            return true;
        }
        return exceeds(userKey(username, ip), properties.loginFailThreshold());
    }

    public void recordFailure(String username, String ip) {
        bump(userKey(username, ip), Duration.ofMinutes(properties.loginLockMinutes()));
        if (StringUtils.hasText(ip)) {
            bump(IP_KEY_PREFIX + ip, Duration.ofMinutes(properties.ipLockMinutes()));
        }
    }

    /**
     * 登录成功：清掉这个账号在这个来源下的失败计数。
     * IP 维度的不清——成功一次不代表之前那一串失败没发生过。
     */
    public void clear(String username, String ip) {
        delete(userKey(username, ip));
    }

    /** 给前端提示「还能试几次」，只反映账号维度 */
    public long remainingAttempts(String username, String ip) {
        String value = read(userKey(username, ip));
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

    private boolean exceeds(String key, int threshold) {
        String value = read(key);
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= threshold;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void bump(String key, Duration ttl) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception e) {
            log.warn("记录登录失败次数失败 key={}", key, e);
        }
    }

    private String read(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取登录失败次数失败 key={}", key, e);
            return null;
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清除登录失败次数失败 key={}", key, e);
        }
    }

    private String userKey(String username, String ip) {
        return USER_KEY_PREFIX + username + ":" + (ip == null ? "-" : ip);
    }
}
