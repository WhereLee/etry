package com.wherelee.edu.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置他人密码。
 * 和用户自助改密的区别：不需要旧密码，重置成功后对方的 token_version +1，
 * 所有已登录设备立即失效。
 */
public record AdminResetPasswordRequest(

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间")
        String newPassword
) {
}
