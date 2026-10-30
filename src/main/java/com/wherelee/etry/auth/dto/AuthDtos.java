package com.wherelee.etry.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 认证相关的请求体。校验信息直接写在这里，控制器只负责调用服务。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入邮箱")
            @Email(message = "邮箱格式不正确")
            @Size(max = 255, message = "邮箱过长")
            String email,

            @NotBlank(message = "请输入密码")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
            String password,

            @NotBlank(message = "请输入昵称")
            @Size(min = 2, max = 20, message = "昵称需为 2-20 个字符")
            String nickname) {
    }

    public record RegisterResponse(boolean needVerify) {
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "请输入验证码")
            @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字") String code) {
    }

    public record ResendRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") String email) {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "请输入密码") String password) {
    }

    public record ForgotRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") String email) {
    }

    public record ResetRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "请输入验证码")
            @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字") String code,
            @NotBlank(message = "请输入新密码")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
            String newPassword) {
    }
}
