package com.wherelee.etry.user;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户信息，绝不包含密码哈希。
 */
public record UserView(
        Long id,
        String email,
        String nickname,
        String bio,
        String avatarUrl,
        long quotaBytes,
        long usedBytes,
        long reservedBytes,
        LocalDateTime createdAt) {
}
