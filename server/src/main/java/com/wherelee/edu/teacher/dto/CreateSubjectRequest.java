package com.wherelee.edu.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(

        @NotBlank(message = "学科编码不能为空")
        @Pattern(regexp = "^[a-z0-9_]{2,30}$", message = "编码需为 2-30 位小写字母、数字或下划线")
        String code,

        @NotBlank(message = "学科名称不能为空")
        @Size(max = 50, message = "名称不能超过 50 字")
        String name,

        Integer sort
) {
}
