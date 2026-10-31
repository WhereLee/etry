package com.wherelee.edu.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建角色。机构里除了学员/讲师/管理员，常见还会要「助教」「教务」这类身份。
 * code 是程序里引用的标识，一旦建好就不再允许修改。
 */
public record CreateRoleRequest(

        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "编码需为 2-30 位大写字母、数字或下划线")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "名称不能超过 50 字")
        String name,

        @Size(max = 200, message = "描述不能超过 200 字")
        String description
) {
}
