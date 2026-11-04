package com.wherelee.edu.teacher;

import com.qcloud.cos.model.ObjectMetadata;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.storage.CosPlaybackService;
import com.wherelee.edu.teacher.dto.TeacherVideoUploadDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class TeacherVideoUploadService {

    private final TeacherContentRepository repository;
    private final CosPlaybackService cos;
    private final UploadProperties properties;

    public TeacherVideoUploadService(TeacherContentRepository repository,
                                     CosPlaybackService cos,
                                     UploadProperties properties) {
        this.repository = repository;
        this.cos = cos;
        this.properties = properties;
    }

    @Transactional
    public TeacherVideoUploadDtos.UploadUrlResponse createUploadUrl(
            Long teacherId, Long seriesId, Long chapterId, Long videoId,
            TeacherVideoUploadDtos.UploadUrlRequest request) {
        requireOwnedVideo(teacherId, seriesId, chapterId, videoId);
        if (request.sizeBytes() > properties.safeMaxSizeBytes()) {
            throw BizException.badRequest("视频文件超过上传大小限制");
        }
        String contentType = normalizeContentType(request.contentType());
        if (!properties.allowedTypes().contains(contentType)) {
            throw BizException.badRequest("不支持的视频格式");
        }
        String extension = extension(contentType);
        String objectKey = "edu/teacher/" + teacherId + "/series/" + seriesId
                + "/videos/" + videoId + "/" + UUID.randomUUID() + extension;
        int ttlMinutes = properties.safeUrlTtlMinutes();
        String uploadUrl = cos.presignedPutUrl(objectKey, contentType, Duration.ofMinutes(ttlMinutes));
        if (repository.markUploading(chapterId, videoId, objectKey,
                contentType, request.filename().trim()) != 1) {
            throw BizException.notFound("视频不存在");
        }
        draftIfPublished(teacherId, seriesId);
        return new TeacherVideoUploadDtos.UploadUrlResponse(
                uploadUrl, "PUT", contentType, objectKey,
                LocalDateTime.now().plusMinutes(ttlMinutes));
    }

    @Transactional
    public TeacherVideoUploadDtos.CompleteResponse complete(
            Long teacherId, Long seriesId, Long chapterId, Long videoId,
            TeacherVideoUploadDtos.CompleteRequest request) {
        TeacherContentRepository.VideoUploadRow video = requireOwnedVideo(
                teacherId, seriesId, chapterId, videoId);
        if (!"UPLOADING".equals(video.mediaStatus())
                || video.pendingObjectKey() == null || video.pendingObjectKey().isBlank()) {
            throw BizException.badRequest("请先上传视频文件");
        }

        ObjectMetadata metadata;
        try {
            metadata = cos.objectMetadata(video.pendingObjectKey());
        } catch (Exception e) {
            log.warn("确认 COS 上传对象失败 key={} cause={}", video.pendingObjectKey(), e.toString());
            throw BizException.badRequest("视频对象不存在或尚未上传完成");
        }
        long size = metadata.getContentLength();
        if (size <= 0 || size > properties.safeMaxSizeBytes()) {
            throw BizException.badRequest("视频对象大小无效");
        }
        String actualType = normalizeContentType(metadata.getContentType());
        if (actualType == null) {
            actualType = video.contentType();
        }
        if (!properties.allowedTypes().contains(actualType)) {
            throw BizException.badRequest("视频对象类型不正确");
        }
        int durationSeconds = request.durationSeconds() > 0
                ? request.durationSeconds() : video.durationSeconds();
        LocalDateTime uploadedAt = LocalDateTime.now();
        if (repository.markReady(chapterId, videoId, size, actualType, durationSeconds, uploadedAt) != 1) {
            throw BizException.badRequest("上传状态已变化，请刷新后重试");
        }
        draftIfPublished(teacherId, seriesId);
        cleanupReplacedObject(video.videoKey(), video.pendingObjectKey());
        return new TeacherVideoUploadDtos.CompleteResponse(
                "READY", size, actualType, uploadedAt);
    }

    private TeacherContentRepository.VideoUploadRow requireOwnedVideo(
            Long teacherId, Long seriesId, Long chapterId, Long videoId) {
        return repository.findVideoForUpload(teacherId, seriesId, chapterId, videoId)
                .orElseThrow(() -> BizException.notFound("视频不存在"));
    }

    private void draftIfPublished(Long teacherId, Long seriesId) {
        repository.findOwnedSeries(teacherId, seriesId)
                .filter(series -> "PUBLISHED".equals(series.status()))
                .ifPresent(series -> repository.updateStatus(teacherId, seriesId, "DRAFT", null));
    }

    private void cleanupReplacedObject(String oldKey, String newKey) {
        if (oldKey == null || oldKey.isBlank() || oldKey.equals(newKey)
                || !oldKey.startsWith("edu/teacher/")) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    cos.deleteObject(oldKey);
                } catch (Exception e) {
                    log.warn("删除被替换视频对象失败 key={} cause={}", oldKey, e.toString());
                }
            }
        });
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
    }
}
