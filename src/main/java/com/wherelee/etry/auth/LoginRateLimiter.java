package com.wherelee.etry.auth;

import com.wherelee.etry.cache.KeyValueStore;
import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.EtryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录限速：同账号连续失败达到上限后临时锁定，同 IP 每分钟请求数超限直接拒绝。
 * 只做拒绝，不改账号状态。
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final String ACCOUNT_KEY = "etry:login:fail:";
    private static final String IP_KEY = "etry:login:ip:";

    private final KeyValueStore keyValueStore;
    private final EtryProperties properties;

    public void checkIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        long count = keyValueStore.incr(IP_KEY + ip);
        if (count == 1) {
            keyValueStore.expire(IP_KEY + ip, Duration.ofMinutes(1));
        }
        if (count > properties.getSecurity().getLoginIpPerMinute()) {
            throw new BusinessException(ErrorCode.LOGIN_TOO_FREQUENT);
        }
    }

    public void checkAccountLocked(String email) {
        String value = keyValueStore.get(ACCOUNT_KEY + email);
        if (value != null && Long.parseLong(value) >= properties.getSecurity().getLoginMaxFailures()) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }
    }

    public void recordFailure(String email) {
        long count = keyValueStore.incr(ACCOUNT_KEY + email);
        keyValueStore.expire(ACCOUNT_KEY + email, Duration.ofMinutes(properties.getSecurity().getLoginLockMinutes()));
        if (count >= properties.getSecurity().getLoginMaxFailures()) {
            // 锁定窗口从这里重新计时
            keyValueStore.expire(ACCOUNT_KEY + email,
                    Duration.ofMinutes(properties.getSecurity().getLoginLockMinutes()));
        }
    }

    public void clearFailures(String email) {
        keyValueStore.delete(ACCOUNT_KEY + email);
    }
}
