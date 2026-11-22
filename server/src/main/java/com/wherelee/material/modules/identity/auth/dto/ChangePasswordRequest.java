package com.wherelee.material.modules.identity.auth.dto;

import com.wherelee.material.modules.platform.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码") String oldPassword,
        @NotBlank(message = "请输入新密码")
        @StrongPassword
        String newPassword
) {
}
