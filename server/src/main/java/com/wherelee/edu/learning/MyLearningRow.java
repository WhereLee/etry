package com.wherelee.edu.learning;

import java.time.LocalDateTime;

/**
 * 「我的学习」列表的扁平查询结果。
 * 课程总数与已完成数在 SQL 里聚合好，避免列表页每行再查两次。
 */
public record MyLearningRow(
        Long courseId,
        String title,
        String coverKey,
        long totalLessons,
        long completedLessons,
        LocalDateTime lastLearnedAt
) {
}
