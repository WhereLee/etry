package com.wherelee.etry.auth;

import com.wherelee.etry.cache.KeyValueStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登出后把 jti 拉黑，直到它自然过期。
 * JWT 本身无状态，没有这一层的话“登出”只是清了个 cookie。
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String PREFIX = "etry:jwt:blacklist:";

    private final KeyValueStore keyValueStore;

    public void add(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        keyValueStore.setEx(PREFIX + jti, "1", ttl);
    }

    public boolean contains(String jti) {
        return jti != null && keyValueStore.exists(PREFIX + jti);
    }
}
