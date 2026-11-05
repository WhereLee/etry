package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.course.api.CourseGovernance;
import com.wherelee.edu.modules.operations.admin.dto.AdminSeriesDtos;
import com.wherelee.edu.modules.platform.common.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminSeriesService {

    private final CourseGovernance courseGovernance;

    public AdminSeriesService(CourseGovernance courseGovernance) {
        this.courseGovernance = courseGovernance;
    }

    public PageResult<AdminSeriesVO> page(String keyword, String status, Long teacherId,
                                          int page, int size) {
        PageResult<CourseGovernance.SeriesSummary> result =
                courseGovernance.page(keyword, status, teacherId, page, size);
        List<AdminSeriesVO> records = result.records().stream().map(this::summary).toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public AdminSeriesDtos.SeriesDetail detail(Long seriesId) {
        CourseGovernance.SeriesDetail detail = courseGovernance.detail(seriesId);
        List<AdminSeriesDtos.ChapterItem> chapters = detail.chapters().stream()
                .map(chapter -> new AdminSeriesDtos.ChapterItem(
                        chapter.id(), chapter.title(), chapter.sort(),
                        chapter.videos().stream()
                                .map(video -> new AdminSeriesDtos.VideoItem(
                                        video.id(), video.title(), video.sort(),
                                        video.mediaStatus(), video.durationSeconds(), video.fileSizeBytes()))
                                .toList()))
                .toList();
        return new AdminSeriesDtos.SeriesDetail(
                detail.id(), detail.title(), detail.summary(), detail.status(),
                detail.saleStatus(), detail.priceCents(), detail.currency(),
                detail.teacherId(), detail.teacherName(), detail.updatedAt(), chapters);
    }

    public void updateStatus(Long seriesId, String status) {
        courseGovernance.updateStatus(seriesId, status);
    }

    public void updateSale(Long seriesId, String saleStatus, long priceCents) {
        courseGovernance.updateSale(seriesId, saleStatus, priceCents);
    }

    private AdminSeriesVO summary(CourseGovernance.SeriesSummary row) {
        AdminSeriesVO vo = new AdminSeriesVO();
        vo.setId(row.id());
        vo.setTitle(row.title());
        vo.setSummary(row.summary());
        vo.setStatus(row.status());
        vo.setSaleStatus(row.saleStatus());
        vo.setPriceCents(row.priceCents());
        vo.setCurrency(row.currency());
        vo.setTeacherId(row.teacherId());
        vo.setTeacherName(row.teacherName());
        vo.setChapterCount(row.chapterCount());
        vo.setVideoCount(row.videoCount());
        vo.setAssignedStudentCount(row.assignedStudentCount());
        vo.setPublishedAt(row.publishedAt());
        vo.setUpdatedAt(row.updatedAt());
        return vo;
    }
}