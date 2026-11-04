package com.wherelee.edu.series.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class SeriesDtos {

    public record SeriesSummary(
            Long id,
            String title,
            String summary,
            String teacherName,
            long chapterCount,
            long videoCount,
            long completedVideoCount
    ) {
    }

    public record VideoItem(
            Long id,
            String title,
            int durationSeconds,
            int positionSeconds,
            boolean completed
    ) {
    }

    public record ChapterItem(Long id, String title, List<VideoItem> videos) {
    }

    public record SeriesDetail(
            Long id,
            String title,
            String summary,
            String teacherName,
            List<ChapterItem> chapters
    ) {
    }

    public record PlayInfo(Long videoId, String title, String playUrl, long urlExpiresAt) {
    }

    public record PlayableVideo(Long id, Long seriesId, String title, String videoKey) {
    }

    public record TeacherSeriesSummary(
            Long id,
            String title,
            String summary,
            String status,
            long chapterCount,
            long videoCount,
            LocalDateTime updatedAt
    ) {
    }

    public record TeacherVideoItem(
            Long id,
            String title,
            int sort,
            String videoKey,
            String mediaStatus,
            int durationSeconds
    ) {
    }

    public record TeacherChapterItem(
            Long id,
            String title,
            int sort,
            List<TeacherVideoItem> videos
    ) {
    }

    public record TeacherSeriesDetail(
            Long id,
            String title,
            String summary,
            String status,
            int sort,
            String teacherName,
            List<TeacherChapterItem> chapters
    ) {
    }

    private SeriesDtos() {
    }
}
