package com.wherelee.edu.teacher;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.storage.CosPlaybackService;
import com.wherelee.edu.teacher.dto.TeacherContentDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Service
public class TeacherContentService {

    private final TeacherContentRepository repository;
    private final CosPlaybackService cos;

    public TeacherContentService(TeacherContentRepository repository, CosPlaybackService cos) {
        this.repository = repository;
        this.cos = cos;
    }

    @Transactional
    public Long createSeries(Long teacherId, TeacherContentDtos.SeriesRequest request) {
        return repository.createSeries(teacherId, request.title().trim(),
                normalizeText(request.summary()), request.sort());
    }

    @Transactional
    public void updateSeries(Long teacherId, Long seriesId, TeacherContentDtos.SeriesRequest request) {
        requireOwnedSeries(teacherId, seriesId);
        int updated = repository.updateSeries(teacherId, seriesId, request.title().trim(),
                normalizeText(request.summary()), request.sort());
        if (updated != 1) {
            throw BizException.notFound("系列不存在");
        }
    }

    @Transactional
    public void updateStatus(Long teacherId, Long seriesId, TeacherContentDtos.StatusRequest request) {
        requireOwnedSeries(teacherId, seriesId);
        String status = request.status();
        if ("PUBLISHED".equals(status)) {
            long chapters = repository.countChapters(seriesId);
            long videos = repository.countVideos(seriesId);
            long pending = repository.countPendingVideos(seriesId);
            if (chapters == 0) {
                throw BizException.badRequest("至少需要一个章节才能发布");
            }
            if (videos == 0) {
                throw BizException.badRequest("至少需要一个视频才能发布");
            }
            if (pending > 0) {
                throw BizException.badRequest("还有 " + pending + " 个视频未就绪，不能发布");
            }
        }
        LocalDateTime publishedAt = "PUBLISHED".equals(status) ? LocalDateTime.now() : null;
        if (repository.updateStatus(teacherId, seriesId, status, publishedAt) != 1) {
            throw BizException.notFound("系列不存在");
        }
    }

    @Transactional
    public Long createChapter(Long teacherId, Long seriesId, TeacherContentDtos.ChapterRequest request) {
        requireOwnedSeries(teacherId, seriesId);
        Long chapterId = repository.createChapter(seriesId, request.title().trim(), request.sort());
        draftIfPublished(teacherId, seriesId);
        return chapterId;
    }

    @Transactional
    public void updateChapter(Long teacherId, Long seriesId, Long chapterId,
                              TeacherContentDtos.ChapterRequest request) {
        requireOwnedChapter(teacherId, seriesId, chapterId);
        if (repository.updateChapter(seriesId, chapterId, request.title().trim(), request.sort()) != 1) {
            throw BizException.notFound("章节不存在");
        }
        draftIfPublished(teacherId, seriesId);
    }

    @Transactional
    public void deleteChapter(Long teacherId, Long seriesId, Long chapterId) {
        requireOwnedChapter(teacherId, seriesId, chapterId);
        if (repository.countVideosInChapter(chapterId) > 0) {
            throw BizException.badRequest("请先删除章节下的视频");
        }
        if (repository.deleteChapter(seriesId, chapterId) != 1) {
            throw BizException.notFound("章节不存在");
        }
        draftIfPublished(teacherId, seriesId);
    }

    @Transactional
    public Long createVideo(Long teacherId, Long seriesId, Long chapterId,
                            TeacherContentDtos.VideoRequest request) {
        requireOwnedChapter(teacherId, seriesId, chapterId);
        Long videoId = repository.createVideo(chapterId, request.title().trim(), request.sort(),
                request.durationSeconds());
        draftIfPublished(teacherId, seriesId);
        return videoId;
    }

    @Transactional
    public void updateVideo(Long teacherId, Long seriesId, Long chapterId, Long videoId,
                            TeacherContentDtos.VideoRequest request) {
        requireOwnedVideo(teacherId, seriesId, chapterId, videoId);
        if (repository.updateVideo(chapterId, videoId, request.title().trim(), request.sort(),
                request.durationSeconds()) != 1) {
            throw BizException.notFound("视频不存在");
        }
        draftIfPublished(teacherId, seriesId);
    }

    @Transactional
    public void deleteVideo(Long teacherId, Long seriesId, Long chapterId, Long videoId) {
        TeacherContentRepository.VideoUploadRow video = repository
                .findVideoForUpload(teacherId, seriesId, chapterId, videoId)
                .orElseThrow(() -> BizException.notFound("视频不存在"));
        if (repository.deleteVideo(chapterId, videoId) != 1) {
            throw BizException.notFound("视频不存在");
        }
        draftIfPublished(teacherId, seriesId);
        cleanupObjectAfterCommit(video.videoKey());
    }

    private TeacherContentRepository.OwnedSeries requireOwnedSeries(Long teacherId, Long seriesId) {
        return repository.findOwnedSeries(teacherId, seriesId)
                .orElseThrow(() -> BizException.notFound("系列不存在"));
    }

    private void requireOwnedChapter(Long teacherId, Long seriesId, Long chapterId) {
        repository.findOwnedChapter(teacherId, seriesId, chapterId)
                .orElseThrow(() -> BizException.notFound("章节不存在"));
    }

    private void requireOwnedVideo(Long teacherId, Long seriesId, Long chapterId, Long videoId) {
        repository.findOwnedVideo(teacherId, seriesId, chapterId, videoId)
                .orElseThrow(() -> BizException.notFound("视频不存在"));
    }

    private void draftIfPublished(Long teacherId, Long seriesId) {
        repository.findOwnedSeries(teacherId, seriesId)
                .filter(series -> "PUBLISHED".equals(series.status()))
                .ifPresent(series -> repository.updateStatus(teacherId, seriesId, "DRAFT", null));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void cleanupObjectAfterCommit(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith("edu/teacher/")) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    cos.deleteObject(objectKey);
                } catch (Exception e) {
                    log.warn("删除视频对象失败 key={} cause={}", objectKey, e.toString());
                }
            }
        });
    }

}
