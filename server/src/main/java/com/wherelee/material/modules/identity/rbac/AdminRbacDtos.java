package com.wherelee.material.modules.identity.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 超级管理员角色管理契约（PRD §2 超级管理员：账号与角色权限管理）。
 */
public final class AdminRbacDtos {

    public record RoleView(
            String code,
            String name,
            String dataScope,
            String remark,
            List<String> permissions
    ) {
    }

    public record AssignRolesRequest(@NotNull List<@NotBlank String> roleCodes) {
    }

    private AdminRbacDtos() {
    }
}