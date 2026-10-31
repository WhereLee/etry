package com.wherelee.edu.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class LearningDtos {

    /** 进度上报：只报播到第几秒，完成与否由服务端按时长算 */
    public record ReportRequest(

            @NotNull(message = "课时 ID 不能为空")
            Long lessonId,

            @NotNull(message = "播放位置不能为空")
            @Min(value = 0, message = "播放位置不能为负")
            Integer positionSeconds
    ) {
    }

    public record LessonProgressVO(
            Long lessonId,
            String title,
            Integer positionSeconds,
            Integer durationSeconds,
            boolean completed
    ) {
    }

    public record CourseProgressVO(
            Long courseId,
            String title,
            long totalLessons,
            long completedLessons,
            int percent,
            List<LessonProgressVO> lessons
    ) {
    }

    public record MyCourseVO(
            Long courseId,
            String title,
            String coverUrl,
            long totalLessons,
            long completedLessons,
            int percent,
            LocalDateTime lastLearnedAt
    ) {
    }
}
