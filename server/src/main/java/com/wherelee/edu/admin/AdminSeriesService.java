package com.wherelee.edu.admin;

import com.wherelee.edu.admin.dto.AdminSeriesDtos;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSeriesService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final AdminSeriesMapper mapper;

    public AdminSeriesService(AdminSeriesMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<AdminSeriesVO> page(String keyword, String status, Long teacherId,
                                          int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
        long total = mapper.count(keyword, normalizedStatus, teacherId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        return new PageResult<>(
                mapper.findPage(keyword, normalizedStatus, teacherId,
                        (safePage - 1) * safeSize, safeSize),
                total, safePage, safeSize);
    }

    public AdminSeriesDtos.SeriesDetail detail(Long seriesId) {
        AdminSeriesHeaderVO header = mapper.findHeader(seriesId);
        if (header == null) {
            throw BizException.notFound("系列不存在");
        }
        Map<Long, List<AdminSeriesDtos.VideoItem>> videosByChapter = new LinkedHashMap<>();
        for (AdminSeriesVideoVO video : mapper.findVideos(seriesId)) {
            videosByChapter.computeIfAbsent(video.getChapterId(), key -> new ArrayList<>())
                    .add(new AdminSeriesDtos.VideoItem(
                            video.getId(), video.getTitle(), video.getSort(),
                            video.getMediaStatus(), video.getDurationSeconds(),
                            video.getFileSizeBytes()));
        }
        List<AdminSeriesDtos.ChapterItem> chapters = mapper.findChapters(seriesId).stream()
                .map(chapter -> new AdminSeriesDtos.ChapterItem(
                        chapter.getId(), chapter.getTitle(), chapter.getSort(),
                        videosByChapter.getOrDefault(chapter.getId(), List.of())))
                .toList();
        return new AdminSeriesDtos.SeriesDetail(
                header.getId(), header.getTitle(), header.getSummary(), header.getStatus(),
                header.getSaleStatus(), header.getPriceCents(), header.getCurrency(),
                header.getTeacherId(), header.getTeacherName(), header.getUpdatedAt(), chapters);
    }

    @Transactional
    public void updateStatus(Long seriesId, String status) {
        AdminSeriesHeaderVO series = mapper.findHeader(seriesId);
        if (series == null) {
            throw BizException.notFound("系列不存在");
        }
        if (status.equals(series.getStatus())) {
            return;
        }
        if (!"DRAFT".equals(status) && !"ARCHIVED".equals(status)) {
            throw BizException.badRequest("管理员只能下架或归档系列");
        }
        mapper.updateStatus(seriesId, status);
    }

    @Transactional
    public void updateSale(Long seriesId, String saleStatus, long priceCents) {
        AdminSeriesHeaderVO series = mapper.findHeader(seriesId);
        if (series == null) {
            throw BizException.notFound("系列不存在");
        }
        if ("ON_SALE".equals(saleStatus)) {
            if (!"PUBLISHED".equals(series.getStatus())) {
                throw BizException.badRequest("只有已发布系列才能开启销售");
            }
            if (priceCents <= 0) {
                throw BizException.badRequest("销售价格必须大于 0");
            }
        }
        if (priceCents > 100_000_00L) {
            throw BizException.badRequest("课程价格过高");
        }
        mapper.updateSale(seriesId, saleStatus, priceCents);
    }
}
