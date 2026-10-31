package com.wherelee.edu.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改角色。不含 code：编码在代码里被按值引用，改了会让相关判断悄悄失效。
 */
public record UpdateRoleRequest(

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "名称不能超过 50 字")
        String name,

        @Size(max = 200, message = "描述不能超过 200 字")
        String description
) {
}
