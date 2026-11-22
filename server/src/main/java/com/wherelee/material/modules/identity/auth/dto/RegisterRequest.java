package com.wherelee.material.modules.identity.auth.dto;

import com.wherelee.material.modules.platform.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Size(min = 3, max = 30, message = "用户名长度需为 3-30 位")
        String username,
        @NotBlank(message = "请输入密码")
        @StrongPassword
        String password,
        @Size(max = 30, message = "昵称最长 30 位")
        String nickname
) {
}