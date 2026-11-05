package com.wherelee.edu.modules.identity.auth.dto;

import com.wherelee.edu.modules.platform.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码") String oldPassword,
        @NotBlank(message = "请输入新密码")
        @StrongPassword
        String newPassword
) {
}
