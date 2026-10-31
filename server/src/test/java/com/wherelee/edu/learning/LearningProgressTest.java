package com.wherelee.edu.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完成判定的边界。
 *
 * 这个判定决定「一门课的进度百分比」，算错了学员会看到假的完成度，
 * 所以单独测而不只靠端到端脚本。
 */
class LearningProgressTest {

    @Test
    @DisplayName("刚好播到 90% 算完成")
    void shouldCompleteAtNinetyPercent() {
        assertThat(LearningProgress.isCompleted(90, 100)).isTrue();
        assertThat(LearningProgress.isCompleted(89, 100)).isFalse();
    }

    @Test
    @DisplayName("播完算完成")
    void shouldCompleteAtEnd() {
        assertThat(LearningProgress.isCompleted(100, 100)).isTrue();
    }

    @Test
    @DisplayName("时长为 0 时不算完成，避免一个请求就把课标成学完")
    void shouldNotCompleteWhenDurationUnknown() {
        assertThat(LearningProgress.isCompleted(0, 0)).isFalse();
        assertThat(LearningProgress.isCompleted(100, 0)).isFalse();
    }

    @Test
    @DisplayName("从没播过不算完成")
    void shouldNotCompleteAtZero() {
        assertThat(LearningProgress.isCompleted(0, 100)).isFalse();
    }

    @Test
    @DisplayName("长视频按比例算，不是按固定秒数")
    void shouldUseRatioNotFixedSeconds() {
        // 一小时 3600 秒，播到 3240 秒即 90%
        assertThat(LearningProgress.isCompleted(3240, 3600)).isTrue();
        assertThat(LearningProgress.isCompleted(3239, 3600)).isFalse();
    }
}
