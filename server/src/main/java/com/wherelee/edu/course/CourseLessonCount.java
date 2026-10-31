package com.wherelee.edu.course;

/** 按课程聚合的课时数，用于列表页一次查完；cnt 是全量，videoCnt 是能学的 */
public record CourseLessonCount(Long courseId, long cnt, long videoCnt) {
}
