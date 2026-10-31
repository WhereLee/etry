package com.wherelee.edu.course;

/** 按课程聚合的课时数，用于列表页一次查完，不再逐条 count */
public record CourseLessonCount(Long courseId, long cnt) {
}
