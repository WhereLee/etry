package com.wherelee.edu.modules.operations.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminSeriesDtos {

    public record StatusRequest(
            @NotBlank(message = "请选择系列状态")
            @Pattern(regexp = "DRAFT|ARCHIVED", message = "管理员只能下架或归档系列")
            String status
    ) {
    }

    public record SaleRequest(
            @NotBlank(message = "请选择销售状态")
            @Pattern(regexp = "OFF_SALE|ON_SALE", message = "销售状态不正确")
            String saleStatus,
            @Min(value = 0, message = "价格不能小于 0")
            long priceCents
    ) {
    }

    public record SeriesDetail(
            Long id,
            String title,
            String summary,
            String status,
            String saleStatus,
            Long priceCents,
            String currency,
            Long teacherId,
            String teacherName,
            LocalDateTime updatedAt,
            List<ChapterItem> chapters
    ) {
    }

    public record ChapterItem(
            Long id,
            String title,
            int sort,
            List<VideoItem> videos
    ) {
    }

    public record VideoItem(
            Long id,
            String title,
            int sort,
            String mediaStatus,
            int durationSeconds,
            Long fileSizeBytes
    ) {
    }

    private AdminSeriesDtos() {
    }
}
