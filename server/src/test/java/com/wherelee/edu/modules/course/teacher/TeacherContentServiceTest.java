package com.wherelee.edu.modules.course.teacher;

import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.storage.CosPlaybackService;
import com.wherelee.edu.modules.course.teacher.dto.TeacherContentDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherContentServiceTest {

    @Mock
    private TeacherContentRepository repository;
    @Mock
    private CosPlaybackService cos;

    private TeacherContentService service;

    @BeforeEach
    void setUp() {
        service = new TeacherContentService(repository, cos);
    }

    @Test
    void publishRequiresAtLeastOneChapter() {
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "DRAFT")));
        when(repository.countChapters(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.updateStatus(2L, 1L,
                new TeacherContentDtos.StatusRequest("PUBLISHED")))
                .isInstanceOf(BizException.class)
                .hasMessage("至少需要一个章节才能发布");
    }

    @Test
    void publishRejectsPendingVideos() {
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "DRAFT")));
        when(repository.countChapters(1L)).thenReturn(1L);
        when(repository.countVideos(1L)).thenReturn(2L);
        when(repository.countPendingVideos(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateStatus(2L, 1L,
                new TeacherContentDtos.StatusRequest("PUBLISHED")))
                .isInstanceOf(BizException.class)
                .hasMessage("还有 1 个视频未就绪，不能发布");
    }

    @Test
    void publishesReadySeries() {
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "DRAFT")));
        when(repository.countChapters(1L)).thenReturn(1L);
        when(repository.countVideos(1L)).thenReturn(1L);
        when(repository.countPendingVideos(1L)).thenReturn(0L);
        when(repository.updateStatus(eq(2L), eq(1L), eq("PUBLISHED"), any(LocalDateTime.class)))
                .thenReturn(1);

        service.updateStatus(2L, 1L, new TeacherContentDtos.StatusRequest("PUBLISHED"));

        verify(repository).updateStatus(eq(2L), eq(1L), eq("PUBLISHED"), any(LocalDateTime.class));
    }

    @Test
    void createVideoStartsPendingUntilUploadCompletes() {
        when(repository.findOwnedChapter(2L, 1L, 10L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedChapter(10L, 1L)));
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "DRAFT")));
        when(repository.createVideo(10L, "1.1 质点", 1, 181)).thenReturn(100L);

        Long id = service.createVideo(2L, 1L, 10L,
                new TeacherContentDtos.VideoRequest("1.1 质点", 1, 181));

        org.assertj.core.api.Assertions.assertThat(id).isEqualTo(100L);
        verify(repository).createVideo(10L, "1.1 质点", 1, 181);
    }

    @Test
    void publishedSeriesReturnsToDraftWhenStructureChanges() {
        when(repository.findOwnedSeries(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.OwnedSeries(1L, "PUBLISHED")));
        when(repository.createChapter(1L, "第一章", 1)).thenReturn(10L);

        service.createChapter(2L, 1L, new TeacherContentDtos.ChapterRequest("第一章", 1));

        verify(repository).updateStatus(2L, 1L, "DRAFT", null);
    }

    @Test
    void cannotPublishAnotherTeachersSeries() {
        when(repository.findOwnedSeries(2L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(2L, 99L,
                new TeacherContentDtos.StatusRequest("PUBLISHED")))
                .isInstanceOf(BizException.class)
                .hasMessage("系列不存在");
        verify(repository, never()).updateStatus(any(), any(), any(), any());
    }
}
