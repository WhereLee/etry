package com.wherelee.edu.notice.dto;

import com.wherelee.edu.notice.Notice;

import java.time.LocalDateTime;

public final class NoticeDtos {

    public record NoticeVO(
            Long id,
            String title,
            String content,
            Long publisherId,
            String status,
            Boolean isTop,
            LocalDateTime publishedAt,
            LocalDateTime createdAt
    ) {
        public static NoticeVO from(Notice n) {
            return new NoticeVO(n.getId(), n.getTitle(), n.getContent(), n.getPublisherId(),
                    n.getStatus(), n.getIsTop(), n.getPublishedAt(), n.getCreatedAt());
        }
    }

    /** 列表页不需要正文，省带宽 */
    public record NoticeSummaryVO(
            Long id,
            String title,
            Boolean isTop,
            String status,
            LocalDateTime publishedAt,
            LocalDateTime createdAt
    ) {
        public static NoticeSummaryVO from(Notice n) {
            return new NoticeSummaryVO(n.getId(), n.getTitle(), n.getIsTop(), n.getStatus(),
                    n.getPublishedAt(), n.getCreatedAt());
        }
    }

    public record SaveRequest(
            @jakarta.validation.constraints.NotBlank(message = "标题不能为空")
            @jakarta.validation.constraints.Size(max = 100, message = "标题不能超过 100 字")
            String title,

            @jakarta.validation.constraints.NotBlank(message = "正文不能为空")
            // 列是 TEXT（65535 字节），中文 3 字节/字，2 万字留出安全余量；
            // 不限的话超长会直接抛 DataIntegrityViolationException，前端只看到「系统繁忙」
            @jakarta.validation.constraints.Size(max = 20000, message = "正文不能超过 20000 字")
            String content,

            @jakarta.validation.constraints.Pattern(
                    regexp = "DRAFT|PUBLISHED|OFFLINE",
                    message = "状态只能是 DRAFT / PUBLISHED / OFFLINE")
            String status,

            Boolean isTop
    ) {
    }
}
