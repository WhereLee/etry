package com.wherelee.edu.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignRolesRequest(
        @NotNull(message = "角色列表不能为空")
        @NotEmpty(message = "至少要保留一个角色")
        List<Long> roleIds
) {
}
