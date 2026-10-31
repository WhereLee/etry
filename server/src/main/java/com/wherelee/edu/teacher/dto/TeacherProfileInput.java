package com.wherelee.edu.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员开号时一并填写的讲师档案。
 *
 * 和 TeacherApplyRequest 的区别：这条不经过审核，账号建好档案直接是 APPROVED，
 * 因为资料是管理员自己填的，等于自审。
 */
public record TeacherProfileInput(

        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 字")
        String realName,

        @Size(max = 50, message = "头衔不能超过 50 字")
        String title,

        @Size(max = 500, message = "简介不能超过 500 字")
        String bio,

        @Min(value = 0, message = "教龄不能为负")
        @Max(value = 60, message = "教龄不能超过 60 年")
        Integer years,

        @NotNull(message = "请选择学科")
        @NotEmpty(message = "至少要选择一个学科")
        @Size(max = 10, message = "最多选择 10 个学科")
        List<Long> subjectIds
) {
}
