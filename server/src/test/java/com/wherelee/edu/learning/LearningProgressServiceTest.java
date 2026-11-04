package com.wherelee.edu.learning;

import com.wherelee.edu.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningProgressServiceTest {

    @Mock
    private LearningProgressRepository repository;

    private LearningProgressService service;

    @BeforeEach
    void setUp() {
        service = new LearningProgressService(repository);
    }

    @Test
    void rejectsVideoNotAssignedToStudent() {
        when(repository.findAccessibleVideo(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 10L,
                new LearningProgressDtos.UpdateRequest(30, 181, false)))
                .isInstanceOf(BizException.class)
                .hasMessage("视频不存在或无权观看");

        verify(repository, never()).upsert(1L, 1L, 10L, 30, 181, false);
    }

    @Test
    void clampsPositionAndCompletesNearEnd() {
        when(repository.findAccessibleVideo(1L, 10L))
                .thenReturn(Optional.of(new LearningProgressRepository.AccessibleVideo(10L, 1L, 181)));

        service.update(1L, 10L, new LearningProgressDtos.UpdateRequest(200, 181, false));

        verify(repository).upsert(1L, 1L, 10L, 181, 181, true);
    }

    @Test
    void usesCatalogDurationWhenClientOmitsIt() {
        when(repository.findAccessibleVideo(1L, 10L))
                .thenReturn(Optional.of(new LearningProgressRepository.AccessibleVideo(10L, 1L, 181)));

        service.update(1L, 10L, new LearningProgressDtos.UpdateRequest(30, 0, false));

        verify(repository).upsert(1L, 1L, 10L, 30, 181, false);
    }

    @Test
    void explicitCompletionMovesPositionToEnd() {
        when(repository.findAccessibleVideo(1L, 10L))
                .thenReturn(Optional.of(new LearningProgressRepository.AccessibleVideo(10L, 1L, 181)));

        service.update(1L, 10L, new LearningProgressDtos.UpdateRequest(30, 181, true));

        verify(repository).upsert(1L, 1L, 10L, 181, 181, true);
    }
}
