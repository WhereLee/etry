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
            /** 最后播到哪。回看会退，用于下次续播 */
            Integer positionSeconds,
            /** 到达过的最远位置。只增不减，用于进度展示 */
            Integer furthestSeconds,
            Integer durationSeconds,
            boolean completed
    ) {
    }

    public record CourseProgressVO(
            Long courseId,
            String title,
            /** 是否强制按顺序学：前端据此渲染锁定态 */
            boolean sequential,
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
            /** 课程状态：非 PUBLISHED 时前端应置灰，不要点进去才发现课程已下架 */
            String status,
            /** 我和这门课的关系：JOINED / LEARNING / COMPLETED */
            String relationStatus,
            long totalLessons,
            long completedLessons,
            int percent,
            LocalDateTime lastLearnedAt
    ) {
    }
}
