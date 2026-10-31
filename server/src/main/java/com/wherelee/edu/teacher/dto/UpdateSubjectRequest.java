package com.wherelee.edu.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 改学科：只允许改名称和排序，编码不可变 */
public record UpdateSubjectRequest(

        @NotBlank(message = "学科名称不能为空")
        @Size(max = 50, message = "名称不能超过 50 字")
        String name,

        Integer sort
) {
}
