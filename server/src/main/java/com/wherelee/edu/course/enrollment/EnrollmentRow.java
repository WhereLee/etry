package com.wherelee.edu.course.enrollment;

import java.time.LocalDateTime;

/**
 * "我的学习"一行。字段顺序必须与 CourseEnrollmentMapper.findActiveByUser 的
 * SELECT 列顺序完全一致——MyBatis 按位置赋值，错一位就会把标题塞进时间字段。
 */
public record EnrollmentRow(
        Long courseId,
        String title,
        String coverKey,
        String courseStatus,
        String relationStatus,
        Integer lessonCountSnapshot,
        LocalDateTime lastActiveAt,
        Long totalLessons,
        Long completedLessons
) {
}
