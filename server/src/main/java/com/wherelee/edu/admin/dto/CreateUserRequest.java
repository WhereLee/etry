package com.wherelee.edu.admin.dto;

import com.wherelee.edu.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
        String username,
        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称不能超过 20 字")
        String nickname,
        @NotBlank(message = "初始密码不能为空")
        @StrongPassword
        String initialPassword,
        @NotBlank(message = "请选择角色")
        @Pattern(regexp = "STUDENT", message = "管理员只能直接创建学员，教师必须通过邀请注册")
        String role
) {
}
