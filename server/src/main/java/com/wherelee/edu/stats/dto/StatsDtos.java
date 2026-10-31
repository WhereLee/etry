package com.wherelee.edu.stats.dto;

public final class StatsDtos {

    /**
     * 看板总览。按课程状态拆开，是因为运营最关心的就是「有几门在审、几门在架」。
     */
    public record OverviewVO(
            long courseTotal,
            long coursePublished,
            long coursePending,
            long courseDraft,
            long courseOffline,
            long courseRejected,
            long teacherActive,
            long teacherPending,
            long userTotal,
            long userActive,
            long learningRecords,
            long lessonCompleted
    ) {
    }

    public record TopCourseVO(Long courseId, String title, long learners) {
    }
}
