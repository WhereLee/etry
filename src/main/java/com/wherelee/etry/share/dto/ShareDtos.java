package com.wherelee.etry.share.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class ShareDtos {

    private ShareDtos() {
    }

    public record CreateShareRequest(
            @NotNull(message = "缺少文件 id") Long fileId,
            Integer expireDays,
            @Pattern(regexp = "^$|\\d{4}", message = "提取码为 4 位数字") String code) {
    }

    public record UnlockRequest(String code) {
    }

    public record UnlockResponse(String url) {
    }
}
