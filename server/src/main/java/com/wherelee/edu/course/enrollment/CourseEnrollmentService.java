package com.wherelee.edu.course.enrollment;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.course.Course;
import com.wherelee.edu.course.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 加入 / 移出 / 推进关系状态。
 *
 * 四个状态之间的规则（对应学员业务规则 C1、G1、F1-3）：
 *   JOINED    --开始播-->  LEARNING
 *   JOINED    --直接学完--> COMPLETED
 *   LEARNING  --全部学完--> COMPLETED
 *   COMPLETED --回看-->    不退回 LEARNING（已学完就是已学完）
 *   任意       --用户移出--> QUIT（记录保留，进度不清）
 *   QUIT      --重新加入--> JOINED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseEnrollmentService {

    private final CourseEnrollmentMapper enrollmentMapper;
    private final CourseMapper courseMapper;

    /** 加入课程。重复点不报错，幂等。 */
    @Transactional
    public void enroll(Long userId, Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        int lessonCount = enrollmentMapper.countLearnableLessons(courseId);
        CourseEnrollment existing = enrollmentMapper.findByUserAndCourse(userId, courseId);
        if (existing == null) {
            enrollmentMapper.insertIgnore(userId, courseId, CourseEnrollment.STATUS_JOINED, lessonCount);
            log.info("加入课程 userId={} courseId={}", userId, courseId);
            return;
        }
        // 移出过的重新加入：状态要按已有的学习记录恢复，
        // 一律回 JOINED 会出现「状态是未开始、进度却 100%」的矛盾。
        String next = existing.getStatus();
        if (CourseEnrollment.STATUS_QUIT.equals(existing.getStatus())) {
            int done = enrollmentMapper.countCompletedLessons(userId, courseId);
            if (lessonCount > 0 && done >= lessonCount) {
                next = CourseEnrollment.STATUS_COMPLETED;
            } else if (done > 0) {
                next = CourseEnrollment.STATUS_LEARNING;
            } else {
                next = CourseEnrollment.STATUS_JOINED;
            }
        }
        enrollmentMapper.updateStatus(userId, courseId, next, lessonCount);
        log.info("加入课程（已存在） userId={} courseId={} status={}", userId, courseId, next);
    }

    /** 移出我的学习。没加入过也算成功，前端不用先查状态。 */
    @Transactional
    public void quit(Long userId, Long courseId) {
        CourseEnrollment existing = enrollmentMapper.findByUserAndCourse(userId, courseId);
        if (existing == null) {
            return;
        }
        enrollmentMapper.updateStatus(userId, courseId, CourseEnrollment.STATUS_QUIT,
                enrollmentMapper.countLearnableLessons(courseId));
        log.info("移出我的学习 userId={} courseId={}", userId, courseId);
    }

    /**
     * 开始播放时调用：把关系推进到"在学"。
     *
     * 正常路径只有一条 UPDATE，没有 COUNT、没有先查再写——
     * 播放器每 10 秒上报一次，这里不能有重查询。
     * 只有第一次（还没有关系记录）才去数一次课时数。
     */
    public void markLearning(Long userId, Long courseId) {
        int updated = enrollmentMapper.touchLearning(userId, courseId);
        if (updated == 0) {
            enrollmentMapper.insertIgnore(userId, courseId, CourseEnrollment.STATUS_LEARNING,
                    enrollmentMapper.countLearnableLessons(courseId));
        }
    }

    /** 全部课时完成后调用 */
    public void markCompleted(Long userId, Long courseId, int lessonCount) {
        int updated = enrollmentMapper.markCompleted(userId, courseId, lessonCount);
        if (updated == 0) {
            // 没改到行，可能是还没有关系记录（直接播放到学完）。移出的不补，保持移出。
            CourseEnrollment existing = enrollmentMapper.findByUserAndCourse(userId, courseId);
            if (existing == null) {
                enrollmentMapper.insertIgnore(userId, courseId, CourseEnrollment.STATUS_COMPLETED, lessonCount);
            }
        }
    }

    /** 这门课是不是"在我的学习里"。移出的不算。 */
    public boolean isEnrolled(Long userId, Long courseId) {
        if (userId == null) {
            return false;
        }
        CourseEnrollment e = enrollmentMapper.findByUserAndCourse(userId, courseId);
        return e != null && !CourseEnrollment.STATUS_QUIT.equals(e.getStatus());
    }
}
