package com.wherelee.edu.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class MaterialDtos {

    public record TicketRequest(

            @NotBlank(message = "文件名不能为空")
            String fileName,

            @NotBlank(message = "文件类型不能为空")
            String contentType,

            @NotNull(message = "文件大小不能为空")
            @Min(value = 1, message = "文件大小必须大于 0")
            Long sizeBytes
    ) {
    }

    public record TicketVO(String key, String uploadUrl, int expiresInSeconds) {
    }

    /** 登记资料：文件已经直传完毕，这里只写元信息 */
    public record SaveRequest(

            @NotBlank(message = "资料标题不能为空")
            @Size(max = 100, message = "标题不能超过 100 字")
            String title,

            @NotBlank(message = "文件名不能为空")
            @Size(max = 200, message = "文件名不能超过 200 字")
            String fileName,

            @NotBlank(message = "文件标识不能为空")
            String fileKey,

            Integer sort
    ) {
    }

    public record MaterialVO(
            Long id,
            String title,
            String fileName,
            long sizeBytes,
            String downloadUrl,
            LocalDateTime createdAt
    ) {
    }
}
