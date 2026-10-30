package com.wherelee.edu.rbac.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 给角色重新配权限：整体覆盖，不是增量。传空数组等于把这个角色的权限清空。
 */
public record AssignPermissionsRequest(
        @NotNull(message = "权限列表不能为 null，清空请传空数组")
        List<Long> permissionIds
) {
}
