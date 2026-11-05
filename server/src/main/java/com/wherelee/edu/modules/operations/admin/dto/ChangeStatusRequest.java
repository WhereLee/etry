package com.wherelee.edu.modules.operations.admin.dto;

import jakarta.validation.constraints.Pattern;

public record ChangeStatusRequest(
        @Pattern(regexp = "ACTIVE|DISABLED", message = "状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}