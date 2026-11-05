package com.wherelee.edu.modules.course.api;

import com.wherelee.edu.modules.platform.common.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程治理应用契约：列表、详情、下架和销售设置。
 */
public interface CourseGovernance {

    PageResult<SeriesSummary> page(String keyword, String status, Long teacherId, int page, int size);

    SeriesDetail detail(Long seriesId);

    void updateStatus(Long seriesId, String status);

    void updateSale(Long seriesId, String saleStatus, long priceCents);

    record SeriesSummary(
            Long id,
            String title,
            String summary,
            String status,
            String saleStatus,
            Long priceCents,
            String currency,
            Long teacherId,
            String teacherName,
            Long chapterCount,
            Long videoCount,
            Long assignedStudentCount,
            LocalDateTime publishedAt,
            LocalDateTime updatedAt
    ) {
    }

    record SeriesDetail(
            Long id,
            String title,
            String summary,
            String status,
            String saleStatus,
            Long priceCents,
            String currency,
            Long teacherId,
            String teacherName,
            LocalDateTime updatedAt,
            List<ChapterItem> chapters
    ) {
    }

    record ChapterItem(Long id, String title, int sort, List<VideoItem> videos) {
    }

    record VideoItem(Long id, String title, int sort, String mediaStatus,
                     int durationSeconds, Long fileSizeBytes) {
    }
}