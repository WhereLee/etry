package com.wherelee.edu.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 已作废 token 的黑名单，只存活到 token 自然过期为止，不会无限增长。
 *
 * Redis 不可用时降级放行：tokenVersion 那道校验不依赖 Redis，
 * 改密码踢人的能力不受影响，不能因为缓存挂了把全站打成 401。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "edu:jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (Exception e) {
            log.warn("写入 token 黑名单失败，已跳过 jti={}", jti, e);
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("查询 token 黑名单失败，降级放行 jti={}", jti, e);
            return false;
        }
    }
}
