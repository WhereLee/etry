package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.learning.LearningProgress;
import com.wherelee.edu.learning.LearningProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 顺序学习的前置校验。
 *
 * 抽出来是因为有两个入口都要拦：
 *   1. 播放：/api/courses/lessons/{id}/play
 *   2. 进度上报：/api/learning/progress —— 只拦播放的话，直接对第 N 节
 *      上报「position = duration」就能把它标成完成，下一节的锁自然就开了。
 *
 * 顺序只算有视频的课时：没视频的课时上报不了进度、永远完不成，
 * 一旦计入，它后面的课时会被永久锁死。
 */
@Component
@RequiredArgsConstructor
public class SequentialChecker {

    private final CourseLessonMapper lessonMapper;
    private final LearningProgressMapper progressMapper;

    /** 课程没开顺序学习时直接放行 */
    public void requirePreviousCompleted(Long userId, Course course, Long lessonId) {
        if (course == null || !course.isSequential()) {
            return;
        }
        List<CourseLesson> ordered = lessonMapper.findOrderedByCourseId(course.getId());
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(lessonId)) {
                index = i;
                break;
            }
        }
        // 第一个课时、或这个课时不在顺序里（比如刚传完视频还没排进来）都放行
        if (index <= 0) {
            return;
        }
        Long previousId = ordered.get(index - 1).getId();
        LearningProgress previous = progressMapper.findByUserAndLesson(userId, previousId);
        if (previous == null || !Boolean.TRUE.equals(previous.getCompleted())) {
            throw BizException.of(ErrorCode.SEQUENTIAL_REQUIRED);
        }
    }
}
