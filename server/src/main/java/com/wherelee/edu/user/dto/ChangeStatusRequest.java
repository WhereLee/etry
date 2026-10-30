package com.wherelee.edu.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeStatusRequest(
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "ACTIVE|DISABLED", message = "状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}
