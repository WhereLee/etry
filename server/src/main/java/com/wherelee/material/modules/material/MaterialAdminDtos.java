package com.wherelee.material.modules.material;

import com.wherelee.material.modules.platform.aop.AuditedResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 运营端资料管理契约。金额单位为分。
 */
public final class MaterialAdminDtos {

    public record AdminItemView(
            Long id,
            String title,
            String category,
            String stage,
            String subject,
            String summary,
            long priceCents,
            String currency,
            int pageCount,
            int previewPages,
            String coverKey,
            String fileKey,
            String saleStatus,
            boolean featured,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) implements AuditedResult {
        @Override
        public Long auditTargetId() {
            return id;
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 20) String category,
            @Size(max = 20) String stage,
            @Size(max = 30) String subject,
            @Size(max = 500) String summary,
            @Positive long priceCents,
            @Positive int pageCount,
            @Positive int previewPages
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 20) String category,
            @Size(max = 20) String stage,
            @Size(max = 30) String subject,
            @Size(max = 500) String summary,
            @Positive long priceCents,
            @Positive int pageCount,
            @Positive int previewPages
    ) {
    }

    public record SaleStatusRequest(@NotBlank String saleStatus) {
    }

    public record FeaturedRequest(@NotNull Boolean featured) {
    }

    private MaterialAdminDtos() {
    }
}