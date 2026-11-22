package com.wherelee.material.modules.material;

import java.util.List;

/**
 * 商城目录公开接口契约。金额单位为分。
 */
public final class MaterialDtos {

    public record ListItemView(
            Long id,
            String title,
            String category,
            String stage,
            String subject,
            String summary,
            long priceCents,
            String currency,
            int pageCount,
            String coverKey
    ) {
    }

    public record DetailView(
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
            String coverKey
    ) {
    }

    public record PageView(List<ListItemView> items, long total, int page, int size) {
    }

    /** 已购书架行。downloadable=false 表示文件缺失，前端置灰下载。 */
    public record LibraryItemView(
            Long id,
            String title,
            String category,
            String stage,
            String subject,
            int pageCount,
            String coverKey,
            boolean downloadable
    ) {
    }

    private MaterialDtos() {
    }
}