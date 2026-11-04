package com.wherelee.edu.teacher;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.series.dto.SeriesDtos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherSeriesServiceTest {

    @Test
    void cannotOpenAnotherTeachersSeries() {
        TeacherContentRepository repository = mock(TeacherContentRepository.class);
        TeacherSeriesService service = new TeacherSeriesService(repository);
        when(repository.findSeriesHeader(2L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(2L, 99L))
                .isInstanceOf(BizException.class)
                .hasMessage("系列不存在");
    }

    @Test
    void buildsOwnedSeriesDetail() {
        TeacherContentRepository repository = mock(TeacherContentRepository.class);
        TeacherSeriesService service = new TeacherSeriesService(repository);
        when(repository.findSeriesHeader(2L, 1L))
                .thenReturn(Optional.of(new TeacherContentRepository.SeriesHeader(
                        1L, "物理必修一", "简介", "PUBLISHED", 1, "物理老师")));
        when(repository.findChapters(1L))
                .thenReturn(List.of(new TeacherContentRepository.ChapterRow(10L, "第一章", 1)));
        when(repository.findVideos(1L))
                .thenReturn(List.of(new TeacherContentRepository.VideoRow(
                        100L, 10L, "1.1 质点", 1, "videos/demo.mp4", "READY", 181)));

        SeriesDtos.TeacherSeriesDetail detail = service.detail(2L, 1L);

        assertThat(detail.title()).isEqualTo("物理必修一");
        assertThat(detail.chapters()).hasSize(1);
        assertThat(detail.chapters().get(0).videos()).hasSize(1);
    }
}
