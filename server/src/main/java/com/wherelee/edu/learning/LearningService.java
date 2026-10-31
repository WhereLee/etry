package com.wherelee.edu.learning;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.course.Course;
import com.wherelee.edu.course.CourseLesson;
import com.wherelee.edu.course.CourseLessonMapper;
import com.wherelee.edu.course.CourseMapper;
import com.wherelee.edu.learning.dto.LearningDtos;
import com.wherelee.edu.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习进度。
 *
 * 两个设计点：
 * 1. 完成与否由服务端算（播到 90%），不信客户端传的 completed——否则一个请求就能把课标成学完
 * 2. 时长取课时表的值，不信客户端——客户端报个更小的时长就能骗过 90% 判定
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningService {

    private static final int MAX_MY_COURSES = 20;

    private final LearningProgressMapper progressMapper;
    private final CourseMapper courseMapper;
    private final CourseLessonMapper lessonMapper;
    private final FileStorage fileStorage;

    /**
     * 上报进度。播放器大概每 10 秒调一次，所以这里不能做重逻辑：
     * 一次课时查询 + 一次课程查询 + 一条 UPSERT，就这些。
     */
    public void report(Long userId, LearningDtos.ReportRequest request) {
        CourseLesson lesson = lessonMapper.findById(request.lessonId());
        if (lesson == null) {
            throw BizException.of(ErrorCode.LESSON_NOT_FOUND);
        }
        // 还没传视频的课时上报进度没有意义，也说明前端传错了 ID
        if (lesson.getVideoKey() == null || lesson.getVideoKey().isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "该课时还没有视频");
        }
        Course course = courseMapper.findById(lesson.getCourseId());
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }

        int duration = lesson.getDurationSeconds() == null ? 0 : lesson.getDurationSeconds();
        int position = request.positionSeconds();
        if (duration > 0 && position > duration) {
            position = duration;
        }
        boolean completed = LearningProgress.isCompleted(position, duration);

        progressMapper.upsert(userId, course.getId(), lesson.getId(), position, duration, completed);
    }

    /** 某门课的进度：每个课时的位置 + 课程总进度 */
    public LearningDtos.CourseProgressVO courseProgress(Long userId, Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }

        List<CourseLesson> lessons = lessonMapper.findByCourseId(courseId);
        Map<Long, LearningProgress> byLesson = progressMapper.findByUserAndCourse(userId, courseId)
                .stream()
                .collect(Collectors.toMap(LearningProgress::getLessonId, p -> p));

        List<LearningDtos.LessonProgressVO> lessonVOs = lessons.stream().map(lesson -> {
            LearningProgress progress = byLesson.get(lesson.getId());
            return new LearningDtos.LessonProgressVO(
                    lesson.getId(),
                    lesson.getTitle(),
                    progress == null ? 0 : progress.getPositionSeconds(),
                    lesson.getDurationSeconds(),
                    progress != null && Boolean.TRUE.equals(progress.getCompleted()));
        }).toList();

        long completedLessons = lessonVOs.stream().filter(LearningDtos.LessonProgressVO::completed).count();
        return new LearningDtos.CourseProgressVO(
                course.getId(), course.getTitle(), lessons.size(), completedLessons,
                percent(completedLessons, lessons.size()), lessonVOs);
    }

    /** 我的学习：按最近学习时间倒序 */
    public List<LearningDtos.MyCourseVO> myCourses(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_MY_COURSES);
        return progressMapper.findRecentCourses(userId, safeLimit).stream()
                .map(row -> new LearningDtos.MyCourseVO(
                        row.courseId(),
                        row.title(),
                        fileStorage.url(row.coverKey()),
                        row.totalLessons(),
                        row.completedLessons(),
                        percent(row.completedLessons(), row.totalLessons()),
                        row.lastLearnedAt()))
                .toList();
    }

    private int percent(long completed, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) (completed * 100 / total);
    }
}
