package com.wherelee.edu.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 登录失败计数，防止密码爆破。
 *
 * 两个维度：
 *   账号 + IP —— 同一个来源对同一个账号连续试错，锁这个组合（不影响别人登录该账号）
 *   纯 IP    —— 同一来源累计错太多次，锁整个 IP（防止换用户名继续猜）
 *
 * 早期只按账号计数，结果是谁都能把别人账号锁掉：输错 5 次就能让对方 15 分钟登不上。
 *
 * 存储策略：优先 Redis（多实例共享），Redis 不可用时降级到进程内滑动窗口。
 * 降级期间计数是单实例的、重启就丢，但总比「Redis 一挂就完全不锁」强。
 * 降级是有意为之的取舍：缓存故障不该把所有人挡在门外，也不该让爆破变成无限次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String USER_KEY_PREFIX = "edu:login:fail:";
    private static final String IP_KEY_PREFIX = "edu:login:ip:";

    /** 兜底计数的 key 上限，防止长时间运行后内存无限增长 */
    private static final int MAX_LOCAL_KEYS = 10_000;

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties properties;

    /** Redis 不可用时的兜底：key -> 失败时间戳队列 */
    private final Map<String, Deque<Long>> localCounters = new ConcurrentHashMap<>();

    /** 登录前判断：IP 被锁，或这个来源对这个账号的失败次数已达阈值 */
    public boolean isLocked(String username, String ip) {
        if (StringUtils.hasText(ip)
                && exceeds(IP_KEY_PREFIX + ip, properties.ipFailThreshold(),
                Duration.ofMinutes(properties.ipLockMinutes()))) {
            log.warn("登录被锁 ip={}", ip);
            return true;
        }
        return exceeds(userKey(username, ip), properties.loginFailThreshold(),
                Duration.ofMinutes(properties.loginLockMinutes()));
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
        String key = userKey(username, ip);
        int used;
        try {
            String value = redisTemplate.opsForValue().get(key);
            used = value == null ? 0 : parse(value);
        } catch (Exception e) {
            used = localCount(key, Duration.ofMinutes(properties.loginLockMinutes()));
        }
        return Math.max(0, properties.loginFailThreshold() - used);
    }

    private boolean exceeds(String key, int threshold, Duration ttl) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，登录失败计数改用进程内兜底 key={}", key, e);
            return localCount(key, ttl) >= threshold;
        }
        if (value == null) {
            return false;
        }
        return parse(value) >= threshold;
    }

    private void bump(String key, Duration ttl) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，登录失败计数写入进程内兜底 key={}", key, e);
            recordLocal(key, ttl);
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，清除登录失败计数失败 key={}", key, e);
        }
        // 两个存储都清：Redis 恢复前后都用同一个账号，不能留残量
        localCounters.remove(key);
    }

    private int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void recordLocal(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        long cutoff = now - ttl.toMillis();
        Deque<Long> times = localCounters.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            times.addLast(now);
        }
        if (localCounters.size() > MAX_LOCAL_KEYS) {
            localCounters.entrySet().removeIf(entry -> pruneAndIsEmpty(entry.getValue(), cutoff));
        }
    }

    private int localCount(String key, Duration ttl) {
        Deque<Long> times = localCounters.get(key);
        if (times == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - ttl.toMillis();
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            return times.size();
        }
    }

    private boolean pruneAndIsEmpty(Deque<Long> times, long cutoff) {
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            return times.isEmpty();
        }
    }

    private String userKey(String username, String ip) {
        return USER_KEY_PREFIX + username + ":" + (ip == null ? "-" : ip);
    }
}
