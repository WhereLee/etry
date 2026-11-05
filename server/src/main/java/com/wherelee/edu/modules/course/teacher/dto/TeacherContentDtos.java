package com.wherelee.edu.modules.course.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TeacherContentDtos {

    public record SeriesRequest(
            @NotBlank(message = "请输入系列标题")
            @Size(max = 100, message = "系列标题不能超过 100 字")
            String title,
            @Size(max = 300, message = "系列简介不能超过 300 字")
            String summary,
            @Min(value = 0, message = "排序不能小于 0")
            int sort
    ) {
    }

    public record StatusRequest(
            @NotBlank(message = "请选择系列状态")
            @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED", message = "系列状态不正确")
            String status
    ) {
    }

    public record ChapterRequest(
            @NotBlank(message = "请输入章节标题")
            @Size(max = 100, message = "章节标题不能超过 100 字")
            String title,
            @Min(value = 0, message = "排序不能小于 0")
            int sort
    ) {
    }

    public record VideoRequest(
            @NotBlank(message = "请输入视频标题")
            @Size(max = 100, message = "视频标题不能超过 100 字")
            String title,
            @Min(value = 0, message = "排序不能小于 0")
            int sort,
            @Min(value = 0, message = "视频时长不能小于 0")
            @Max(value = 86400, message = "视频时长超出范围")
            int durationSeconds
    ) {
    }

    private TeacherContentDtos() {
    }
}
