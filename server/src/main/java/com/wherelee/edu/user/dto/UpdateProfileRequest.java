package com.wherelee.edu.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户自助资料。只允许改昵称和简介——
 * 用户名、角色、状态都是管理侧字段，不能由本人改。
 */
public record UpdateProfileRequest(

        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称不能超过 20 字")
        String nickname,

        @Size(max = 200, message = "简介不能超过 200 字")
        String bio
) {
}
