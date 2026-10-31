package com.wherelee.edu.course;

/** 工作台里每门课的一行统计 */
public record CourseStatRow(
        Long courseId,
        String title,
        String status,
        long learners,
        long completers,
        double avgRating,
        long ratingCount,
        long unanswered
) {
}
