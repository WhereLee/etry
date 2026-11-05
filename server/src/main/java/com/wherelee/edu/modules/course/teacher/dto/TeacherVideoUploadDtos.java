package com.wherelee.edu.modules.course.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class TeacherVideoUploadDtos {

    public record UploadUrlRequest(
            @NotBlank(message = "文件名不能为空")
            @Size(max = 255, message = "文件名不能超过 255 字")
            String filename,
            @NotBlank(message = "视频类型不能为空")
            @Size(max = 100, message = "视频类型不能超过 100 字")
            String contentType,
            @Min(value = 1, message = "视频文件不能为空")
            long sizeBytes
    ) {
    }

    public record UploadUrlResponse(
            String uploadUrl,
            String method,
            String contentType,
            String objectKey,
            LocalDateTime expiresAt
    ) {
    }

    public record CompleteRequest(
            @Min(value = 0, message = "视频时长不能小于 0")
            @Max(value = 86400, message = "视频时长超出范围")
            int durationSeconds
    ) {
    }

    public record CompleteResponse(
            String mediaStatus,
            long fileSizeBytes,
            String contentType,
            LocalDateTime uploadedAt
    ) {
    }

    private TeacherVideoUploadDtos() {
    }
}
