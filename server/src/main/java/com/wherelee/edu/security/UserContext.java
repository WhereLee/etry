package com.wherelee.edu.security;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * 当前请求的登录身份。由 JwtAuthFilter 写入，请求结束时清理。
 */
public final class UserContext {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String username, List<String> roles, String jti, Instant expiresAt) {
        HOLDER.set(new Principal(userId, username, List.copyOf(roles), jti, expiresAt));
    }

    public static Principal get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        return require().userId();
    }

    public static List<String> roles() {
        Principal principal = HOLDER.get();
        return principal == null ? List.of() : principal.roles();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static Principal require() {
        Principal principal = HOLDER.get();
        if (principal == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public record Principal(Long userId, String username, List<String> roles, String jti, Instant expiresAt) {
    }
}
