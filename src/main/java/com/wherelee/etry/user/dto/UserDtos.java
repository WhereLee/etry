package com.wherelee.etry.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "请输入昵称")
            @Size(min = 2, max = 20, message = "昵称需为 2-20 个字符")
            String nickname,

            @Size(max = 255, message = "简介最多 255 个字符")
            String bio) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
            String newPassword) {
    }

    public record AvatarPresignRequest(
            @NotNull(message = "缺少文件大小") Long sizeBytes,
            @NotBlank(message = "缺少文件类型") String contentType) {
    }

    public record AvatarCommitRequest(
            @NotBlank(message = "缺少对象 key") String objectKey) {
    }
}
