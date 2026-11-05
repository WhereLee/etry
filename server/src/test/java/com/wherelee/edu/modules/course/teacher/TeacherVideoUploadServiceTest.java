package com.wherelee.edu.modules.course.teacher;

import com.qcloud.cos.model.ObjectMetadata;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.storage.CosPlaybackService;
import com.wherelee.edu.modules.course.teacher.dto.TeacherVideoUploadDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherVideoUploadServiceTest {

    @Mock
    private TeacherContentRepository repository;
    @Mock
    private CosPlaybackService cos;

    private TeacherVideoUploadService service;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties(
                4L * 1024 * 1024 * 1024, 60, List.of("video/mp4", "video/webm"));
        service = new TeacherVideoUploadService(repository, cos, properties);
    }

    @Test
    void createsPresignedUploadAndMarksVideoUploading() {
        when(repository.findVideoForUpload(2L, 1L, 10L, 100L))
                .thenReturn(Optional.of(video("PENDING", null, null)));
        when(cos.presignedPutUrl(any(), eq("video/mp4"), any(Duration.class)))
                .thenReturn("https://cos.example/upload");
        when(repository.markUploading(eq(10L), eq(100L), any(), eq("video/mp4"), eq("lesson.mp4")))
                .thenReturn(1);
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "DRAFT")));

        TeacherVideoUploadDtos.UploadUrlResponse response = service.createUploadUrl(
                2L, 1L, 10L, 100L,
                new TeacherVideoUploadDtos.UploadUrlRequest("lesson.mp4", "video/mp4", 1024));

        assertThat(response.uploadUrl()).isEqualTo("https://cos.example/upload");
        verify(repository).markUploading(eq(10L), eq(100L), any(), eq("video/mp4"), eq("lesson.mp4"));
    }

    @Test
    void rejectsUnsupportedContentType() {
        when(repository.findVideoForUpload(2L, 1L, 10L, 100L))
                .thenReturn(Optional.of(video("PENDING", null, null)));

        assertThatThrownBy(() -> service.createUploadUrl(
                2L, 1L, 10L, 100L,
                new TeacherVideoUploadDtos.UploadUrlRequest("lesson.exe", "application/octet-stream", 1024)))
                .isInstanceOf(BizException.class)
                .hasMessage("不支持的视频格式");
    }

    @Test
    void completeRejectsVideoThatWasNotUploaded() {
        when(repository.findVideoForUpload(2L, 1L, 10L, 100L))
                .thenReturn(Optional.of(video("PENDING", null, null)));

        assertThatThrownBy(() -> service.complete(
                2L, 1L, 10L, 100L, new TeacherVideoUploadDtos.CompleteRequest(181)))
                .isInstanceOf(BizException.class)
                .hasMessage("请先上传视频文件");
    }

    @Test
    void completeVerifiesObjectAndMarksReady() {
        when(repository.findVideoForUpload(2L, 1L, 10L, 100L))
                .thenReturn(Optional.of(video("UPLOADING", "edu/teacher/upload.mp4", "video/mp4")));
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(4096);
        metadata.setContentType("video/mp4");
        when(cos.objectMetadata("edu/teacher/upload.mp4")).thenReturn(metadata);
        when(repository.markReady(eq(10L), eq(100L), eq(4096L), eq("video/mp4"),
                eq(181), any(LocalDateTime.class))).thenReturn(1);
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "DRAFT")));

        TeacherVideoUploadDtos.CompleteResponse response = service.complete(
                2L, 1L, 10L, 100L, new TeacherVideoUploadDtos.CompleteRequest(181));

        assertThat(response.mediaStatus()).isEqualTo("READY");
        assertThat(response.fileSizeBytes()).isEqualTo(4096);
        verify(repository).markReady(eq(10L), eq(100L), eq(4096L), eq("video/mp4"),
                eq(181), any(LocalDateTime.class));
    }

    private TeacherContentRepository.VideoUploadRow video(
            String status, String pendingKey, String contentType) {
        return new TeacherContentRepository.VideoUploadRow(
                100L, 10L, null, pendingKey, status, contentType, 181);
    }
}
