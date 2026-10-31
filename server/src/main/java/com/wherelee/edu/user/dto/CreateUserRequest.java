package com.wherelee.edu.user.dto;

import com.wherelee.edu.teacher.dto.TeacherProfileInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员开号。机构里的老师账号由教务统一开通，不走「自己注册 → 申请 → 审核」那条路。
 *
 * 初始密码由管理员设置，所以账号建好后会被标记「必须改密」，
 * 在对方改密之前，除了改密/看自己/登出之外的接口都会被拒。
 */
public record CreateUserRequest(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
        String username,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 6, max = 32, message = "初始密码长度需在 6-32 位之间")
        String initialPassword,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称不能超过 20 字")
        String nickname,

        @NotNull(message = "请选择角色")
        @NotEmpty(message = "至少要分配一个角色")
        List<Long> roleIds,

        /**
         * 仅在角色包含 TEACHER 时必填：管理员直接把讲师身份和档案一起发出去。
         */
        TeacherProfileInput teacherProfile
) {
}
