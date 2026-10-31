package com.wherelee.edu.stats;

import com.wherelee.edu.course.Course;
import com.wherelee.edu.stats.dto.StatsDtos;
import com.wherelee.edu.teacher.TeacherProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 运营看板。
 *
 * 全部实时查库，没有缓存也没有汇总表：
 * 这些 COUNT 走的都是索引，课程/用户量在万级以下时单次只要几毫秒，
 * 加缓存反而要处理「数据变了缓存没删」的一致性问题。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int MAX_TOP = 20;

    private final StatsMapper statsMapper;

    public StatsDtos.OverviewVO overview() {
        return new StatsDtos.OverviewVO(
                statsMapper.countCourses(),
                statsMapper.countCoursesByStatus(Course.STATUS_PUBLISHED),
                statsMapper.countCoursesByStatus(Course.STATUS_PENDING),
                statsMapper.countCoursesByStatus(Course.STATUS_DRAFT),
                statsMapper.countCoursesByStatus(Course.STATUS_OFFLINE),
                statsMapper.countCoursesByStatus(Course.STATUS_REJECTED),
                statsMapper.countTeachersByStatus(TeacherProfile.STATUS_APPROVED),
                statsMapper.countTeachersByStatus(TeacherProfile.STATUS_PENDING),
                statsMapper.countUsers(),
                statsMapper.countActiveUsers(),
                statsMapper.countLearningRecords(),
                statsMapper.countCompletedLessons());
    }

    public List<StatsDtos.TopCourseVO> topCourses(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_TOP);
        return statsMapper.topCourses(safeLimit).stream()
                .map(row -> new StatsDtos.TopCourseVO(row.courseId(), row.title(), row.learners()))
                .toList();
    }
}
