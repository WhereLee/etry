package com.wherelee.edu.course.dto;

import java.util.List;

public final class DashboardDtos {

    /** 工作台顶部汇总 */
    public record SummaryVO(
            long courseTotal,
            long publishedCourses,
            /** 学过我任何一门课的人数（跨课程去重） */
            long learnerTotal,
            /** 学完至少一门课的学员数 */
            long completerTotal,
            double ratingAvg,
            long ratingTotal,
            /** 有人问但一条回答都没有的问题数——最该先处理的事 */
            long unansweredQuestions
    ) {
    }

    public record CourseStatVO(
            Long courseId,
            String title,
            String status,
            long learners,
            long completers,
            int completionRate,
            double ratingAvg,
            long ratingTotal,
            long unanswered
    ) {
    }

    public record DashboardVO(SummaryVO summary, List<CourseStatVO> courses) {
    }
}
