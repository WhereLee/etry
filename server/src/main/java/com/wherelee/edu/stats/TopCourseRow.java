package com.wherelee.edu.stats;

/** 热门课程的一行：课程 + 学过它的人数 */
public record TopCourseRow(Long courseId, String title, long learners) {
}
