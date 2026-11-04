package com.wherelee.edu.admin.dto;

import com.wherelee.edu.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "临时密码不能为空")
        @StrongPassword
        String newPassword
) {
}
