package com.wherelee.edu.modules.identity.invitation.dto;

import com.wherelee.edu.modules.platform.aop.AuditedResult;
import com.wherelee.edu.modules.platform.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class TeacherInvitationDtos {

    public record CreateRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
            String username,
            @NotBlank(message = "昵称不能为空")
            @Size(max = 20, message = "昵称不能超过 20 字")
            String nickname
    ) {
    }

    public record CreatedResponse(
            Long id,
            String username,
            String nickname,
            String inviteUrl,
            LocalDateTime expiresAt
    ) implements AuditedResult {
        @Override
        public Long auditTargetId() {
            return id;
        }
    }

    public record PreviewRequest(
            @NotBlank(message = "邀请链接无效") String token
    ) {
    }

    public record PreviewResponse(
            String username,
            String nickname,
            LocalDateTime expiresAt
    ) {
    }

    public record AcceptRequest(
            @NotBlank(message = "邀请链接无效") String token,
            @NotBlank(message = "请输入密码")
            @StrongPassword
            String password,
            @NotBlank(message = "请再次输入密码") String confirmPassword
    ) {
    }

    private TeacherInvitationDtos() {
    }
}
