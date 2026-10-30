package com.wherelee.edu.config.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateConfigRequest(
        @NotNull(message = "配置值不能为 null，清空请传空字符串")
        @Size(max = 500, message = "配置值不能超过 500 字")
        String value
) {
}
