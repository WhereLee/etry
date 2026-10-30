package com.wherelee.etry.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public final class FileDtos {

    private FileDtos() {
    }

    public record PresignRequest(
            @NotBlank(message = "缺少文件名")
            @Size(max = 255, message = "文件名过长")
            String name,

            @NotNull(message = "缺少文件大小")
            Long sizeBytes) {
    }

    public record PresignResponse(
            Long fileId,
            String objectKey,
            String contentType,
            String uploadUrl,
            String method,
            Map<String, String> formFields,
            Map<String, String> headers,
            Instant expireAt) {
    }

    public record RenameRequest(
            @NotBlank(message = "请输入文件名")
            @Size(max = 255, message = "文件名过长")
            String name) {
    }

    public record FileView(
            Long id,
            String name,
            long sizeBytes,
            String contentType,
            LocalDateTime createdAt) {
    }

    public record PendingFileView(
            Long id,
            String name,
            long sizeBytes,
            LocalDateTime createdAt,
            LocalDateTime expireAt) {
    }

    public record DownloadUrl(String url) {
    }

    public record PageView<T>(
            java.util.List<T> items,
            int page,
            int size,
            long total) {
    }
}
