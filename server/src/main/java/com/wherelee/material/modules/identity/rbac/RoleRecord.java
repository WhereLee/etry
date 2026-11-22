package com.wherelee.material.modules.identity.rbac;

import java.time.LocalDateTime;

/**
 * RBAC 角色。dataScope：SELF 仅本人数据 / ALL 全量（管理端角色）。
 */
public record RoleRecord(
        Long id,
        String code,
        String name,
        String dataScope,
        boolean builtIn,
        String remark,
        LocalDateTime createdAt
) {
    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_ALL = "ALL";
}