package com.wherelee.edu.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TeacherDtos {

    public record SeriesSummary(
            Long id,
            String title,
            String summary,
            String status,
            long chapterCount,
            long videoCount,
            LocalDateTime updatedAt
    ) {
    }

    public record VideoItem(
            Long id,
            String title,
            int durationSeconds,
            String videoKey,
            int sort
    ) {
    }

    public record ChapterItem(
            Long id,
            String title,
            int sort,
            List<VideoItem> videos
    ) {
    }

    public record SeriesDetail(
            Long id,
            String title,
            String summary,
            String status,
            LocalDateTime updatedAt,
            List<ChapterItem> chapters
    ) {
    }

    public record CreateSeriesRequest(
            @NotBlank(message = "系列标题不能为空")
            @Size(max = 100, message = "系列标题不能超过 100 字")
            String title,
            @Size(max = 300, message = "系列简介不能超过 300 字")
            String summary
    ) {
    }

    public record UpdateSeriesRequest(
            @NotBlank(message = "系列标题不能为空")
            @Size(max = 100, message = "系列标题不能超过 100 字")
            String title,
            @Size(max = 300, message = "系列简介不能超过 300 字")
            String summary
    ) {
    }

    public record ChangeStatusRequest(
            @NotBlank(message = "请选择状态")
            @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED", message = "不支持的状态")
            String status
    ) {
    }

    public record ChapterRequest(
            @NotBlank(message = "章节标题不能为空")
            @Size(max = 100, message = "章节标题不能超过 100 字")
            String title,
            @Min(value = 0, message = "排序不能小于 0")
            Integer sort
    ) {
    }

    public record VideoRequest(
            @NotBlank(message = "视频标题不能为空")
            @Size(max = 100, message = "视频标题不能超过 100 字")
            String title,
            @Min(value = 0, message = "视频时长不能小于 0")
            @Max(value = 86400, message = "视频时长超出范围")
            Integer durationSeconds,
            @Size(max = 255, message = "视频对象键不能超过 255 字")
            String videoKey,
            @Min(value = 0, message = "排序不能小于 0")
            Integer sort
    ) {
    }

    private TeacherDtos() {
    }
}
