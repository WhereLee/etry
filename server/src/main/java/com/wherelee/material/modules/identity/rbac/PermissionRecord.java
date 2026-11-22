package com.wherelee.material.modules.identity.rbac;

/**
 * RBAC 权限点。code 形如 material:update、order:refund。
 */
public record PermissionRecord(
        Long id,
        String code,
        String name,
        String module
) {
}