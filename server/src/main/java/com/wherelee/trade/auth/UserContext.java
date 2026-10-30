package com.wherelee.trade.auth;

import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.user.User;

import java.time.Instant;

/**
 * 当前请求的登录身份。由 JwtAuthFilter 写入，请求结束时清理。
 */
public final class UserContext {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String username, String role, String jti, Instant expiresAt) {
        HOLDER.set(new Principal(userId, username, role, jti, expiresAt));
    }

    public static Principal get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        Principal principal = require();
        return principal.userId();
    }

    public static boolean isAdmin() {
        Principal principal = HOLDER.get();
        return principal != null && User.ROLE_ADMIN.equals(principal.role());
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

    public record Principal(Long userId, String username, String role, String jti, Instant expiresAt) {
    }
}
