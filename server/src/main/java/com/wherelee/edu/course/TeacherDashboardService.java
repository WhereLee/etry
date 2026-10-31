package com.wherelee.edu.course;

import com.wherelee.edu.course.dto.DashboardDtos;
import com.wherelee.edu.teacher.TeacherProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 讲师工作台。
 *
 * 全部实时聚合，不落汇总表：一个讲师名下通常几门课，
 * 相关子查询在这个数据量下是毫秒级。等哪个讲师有上百门课时再说。
 */
@Service
@RequiredArgsConstructor
public class TeacherDashboardService {

    private final TeacherDashboardMapper dashboardMapper;
    private final CourseQuestionMapper questionMapper;
    private final CourseTeacherService teacherService;

    public DashboardDtos.DashboardVO dashboard(Long userId) {
        TeacherProfile profile = teacherService.requireApprovedTeacher(userId);
        Long teacherId = profile.getId();

        RatingStat rating = dashboardMapper.ratingStat(teacherId);
        DashboardDtos.SummaryVO summary = new DashboardDtos.SummaryVO(
                dashboardMapper.countCourses(teacherId),
                dashboardMapper.countPublishedCourses(teacherId),
                dashboardMapper.countLearners(teacherId),
                dashboardMapper.countCompleters(teacherId),
                round1(rating.avgRating()),
                rating.total(),
                questionMapper.countUnansweredByTeacher(teacherId));

        List<DashboardDtos.CourseStatVO> courses = dashboardMapper.courseStats(teacherId).stream()
                .map(row -> new DashboardDtos.CourseStatVO(
                        row.courseId(), row.title(), row.status(),
                        row.learners(), row.completers(),
                        rate(row.completers(), row.learners()),
                        round1(row.avgRating()), row.ratingCount(), row.unanswered()))
                .toList();

        return new DashboardDtos.DashboardVO(summary, courses);
    }

    /** 完课率 = 完课人数 / 学过的人数 */
    private int rate(long completers, long learners) {
        return learners <= 0 ? 0 : (int) (completers * 100 / learners);
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
