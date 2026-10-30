package com.wherelee.edu.auth.dto;

import java.util.List;

/**
 * 当前登录用户的画像。权限列表在阶段 3 接入 RBAC 后补上。
 */
public record UserMeVO(
        Long id,
        String username,
        String nickname,
        String status,
        List<String> roles,
        List<String> permissions
) {
}
