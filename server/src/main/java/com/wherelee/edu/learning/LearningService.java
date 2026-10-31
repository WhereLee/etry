package com.wherelee.edu.learning;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.course.Course;
import com.wherelee.edu.course.CourseLesson;
import com.wherelee.edu.course.CourseLessonMapper;
import com.wherelee.edu.course.CourseMapper;
import com.wherelee.edu.course.SequentialChecker;
import com.wherelee.edu.course.enrollment.CourseEnrollmentMapper;
import com.wherelee.edu.course.enrollment.CourseEnrollmentService;
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
    private final SequentialChecker sequentialChecker;
    private final LearningProperties learningProperties;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final CourseEnrollmentService enrollmentService;

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
        // 和播放接口用同一套顺序校验：只拦播放的话，直接上报第 N 节完成就能解锁下一节
        sequentialChecker.requirePreviousCompleted(userId, course, lesson.getId());

        int duration = lesson.getDurationSeconds() == null ? 0 : lesson.getDurationSeconds();
        int position = request.positionSeconds();
        if (duration > 0 && position > duration) {
            position = duration;
        }

        LearningProgress existing = progressMapper.findByUserAndLesson(userId, lesson.getId());
        // 完成判定看「最远到过哪」，不看「此刻停在哪」：回看开头不该把已到过的进度抹掉
        int furthest = Math.max(position,
                existing == null || existing.getFurthestSeconds() == null ? 0 : existing.getFurthestSeconds());
        boolean reachedEnd = LearningProgress.isCompleted(furthest, duration);
        boolean completed = reachedEnd
                && (!learningProperties.strictMode() || enoughTimeElapsed(existing, duration));

        progressMapper.upsert(userId, course.getId(), lesson.getId(), position, duration, completed);

        // 播放本身就是最强的「加入」信号：没加入过的人在这里补一条关系。
        // 正常路径只有一条 UPDATE，不数课时——这是每 10 秒走一次的路径
        enrollmentService.markLearning(userId, course.getId());

        // 只有「本次上报刚好把这节学完」才去数一遍总课时和已完成数
        boolean justCompleted = completed && (existing == null || !Boolean.TRUE.equals(existing.getCompleted()));
        if (justCompleted) {
            int lessonCount = enrollmentMapper.countLearnableLessons(course.getId());
            if (lessonCount > 0
                    && progressMapper.countCompletedByUserAndCourse(userId, course.getId()) >= lessonCount) {
                enrollmentService.markCompleted(userId, course.getId(), lessonCount);
            }
        }
    }

    /**
     * 严格模式用：从第一次上报到现在，真实经过的秒数要够 90% 的课时时长。
     *
     * 挡的是「一次请求直接把位置报到底」：那时 first_reported_at 就是此刻，
     * 经过时间为 0，永远不成立。正常看到结尾的人，从开头第一条上报算起，时间自然够。
     */
    private boolean enoughTimeElapsed(LearningProgress existing, int duration) {
        if (duration <= 0) {
            return false;
        }
        if (existing == null || existing.getFirstReportedAt() == null) {
            return false;
        }
        long elapsed = java.time.Duration
                .between(existing.getFirstReportedAt(), java.time.LocalDateTime.now())
                .toSeconds();
        return elapsed >= duration * LearningProgress.COMPLETE_RATIO;
    }

    /** 某门课的进度：每个课时的位置 + 课程总进度 */
    public LearningDtos.CourseProgressVO courseProgress(Long userId, Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }

        // 按「章节 sort、课时 sort」取，且只含有视频的课时：
        // 没视频的课时上报不了进度，计入分母会让完成率永远到不了 100%
        List<CourseLesson> lessons = lessonMapper.findOrderedByCourseId(courseId);
        Map<Long, LearningProgress> byLesson = progressMapper.findByUserAndCourse(userId, courseId)
                .stream()
                .collect(Collectors.toMap(LearningProgress::getLessonId, p -> p));

        List<LearningDtos.LessonProgressVO> lessonVOs = lessons.stream().map(lesson -> {
            LearningProgress progress = byLesson.get(lesson.getId());
            return new LearningDtos.LessonProgressVO(
                    lesson.getId(),
                    lesson.getTitle(),
                    progress == null ? 0 : progress.getPositionSeconds(),
                    progress == null || progress.getFurthestSeconds() == null ? 0 : progress.getFurthestSeconds(),
                    lesson.getDurationSeconds(),
                    progress != null && Boolean.TRUE.equals(progress.getCompleted()));
        }).toList();

        long completedLessons = lessonVOs.stream().filter(LearningDtos.LessonProgressVO::completed).count();
        return new LearningDtos.CourseProgressVO(
                course.getId(), course.getTitle(), course.isSequential(), lessons.size(), completedLessons,
                percent(completedLessons, lessons.size()), lessonVOs);
    }

    /** 我的学习：以关系表为主干，按最近活跃倒序 */
    public List<LearningDtos.MyCourseVO> myCourses(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_MY_COURSES);
        return enrollmentMapper.findActiveByUser(userId, safeLimit).stream()
                .map(row -> {
                    long total = row.totalLessons() == null ? 0 : row.totalLessons();
                    long done = row.completedLessons() == null ? 0 : row.completedLessons();
                    return new LearningDtos.MyCourseVO(
                            row.courseId(),
                            row.title(),
                            fileStorage.url(row.coverKey()),
                            row.courseStatus(),
                            resolveRelation(row.relationStatus(), done, total),
                            total,
                            done,
                            percent(done, total),
                            row.lastActiveAt());
                })
                .toList();
    }

    /**
     * 状态按事实算，不完全信字面值。
     *
     * 学员学完一课后，讲师加了新课时，库里状态还停在 COMPLETED，
     * 但实际已完成的节数已经少于总节数。这种时候不能给用户看「已学完 + 83%」。
     * 读的时候降一级，不靠写入方去补——讲师端不需要知道学员的关系表。
     */
    private static String resolveRelation(String status, long done, long total) {
        if (com.wherelee.edu.course.enrollment.CourseEnrollment.STATUS_COMPLETED.equals(status)
                && total > 0 && done < total) {
            return com.wherelee.edu.course.enrollment.CourseEnrollment.STATUS_LEARNING;
        }
        return status;
    }

    /** 百分比封顶 100：删课时留下的历史脏进度不能再抛 150% 这种值给前端 */
    private int percent(long completed, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.min(100, completed * 100 / total);
    }
}
