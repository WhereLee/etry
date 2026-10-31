package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.QaDtos;
import com.wherelee.edu.teacher.TeacherProfile;
import com.wherelee.edu.teacher.TeacherProfileMapper;
import com.wherelee.edu.user.User;
import com.wherelee.edu.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程问答。
 *
 * 谁能做什么：
 *   任何登录用户 —— 提问、回答（学员之间也能互相答）
 *   提问人或课程讲师 —— 标记已解决
 *   不做「只有讲师能回答」：教培场景里同学互助很常见，限制反而降低活跃度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseQaService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final CourseQuestionMapper questionMapper;
    private final CourseAnswerMapper answerMapper;
    private final CourseMapper courseMapper;
    private final TeacherProfileMapper profileMapper;
    private final UserMapper userMapper;

    @Transactional
    public Long ask(Long userId, Long courseId, QaDtos.AskRequest request) {
        requirePublishedCourse(courseId);
        CourseQuestion question = new CourseQuestion();
        question.setCourseId(courseId);
        question.setUserId(userId);
        question.setContent(request.content().trim());
        questionMapper.insert(question);
        log.info("课程提问 courseId={} questionId={} userId={}", courseId, question.getId(), userId);
        return question.getId();
    }

    public PageResult<QaDtos.QuestionVO> page(Long courseId, int page, int size) {
        requirePublishedCourse(courseId);
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = questionMapper.countByCourse(courseId);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<QaDtos.QuestionVO> records = questionMapper
                .findPageByCourse(courseId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new QaDtos.QuestionVO(row.id(), row.userId(), row.nickname(),
                        row.content(), Boolean.TRUE.equals(row.resolved()), row.answerCount(),
                        row.createdAt()))
                .toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    public QaDtos.QuestionDetailVO detail(Long questionId) {
        CourseQuestion question = requireQuestion(questionId);
        requirePublishedCourse(question.getCourseId());

        User asker = userMapper.findById(question.getUserId());
        List<QaDtos.AnswerVO> answers = answerMapper.findByQuestionId(questionId).stream()
                .map(row -> new QaDtos.AnswerVO(row.id(), row.userId(), row.nickname(),
                        row.content(), row.createdAt()))
                .toList();

        return new QaDtos.QuestionDetailVO(
                question.getId(),
                question.getCourseId(),
                question.getUserId(),
                asker == null ? null : asker.getNickname(),
                question.getContent(),
                Boolean.TRUE.equals(question.getResolved()),
                question.getCreatedAt(),
                answers);
    }

    @Transactional
    public Long answer(Long userId, Long questionId, QaDtos.AnswerRequest request) {
        CourseQuestion question = requireQuestion(questionId);
        requirePublishedCourse(question.getCourseId());

        CourseAnswer answer = new CourseAnswer();
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setContent(request.content().trim());
        answerMapper.insert(answer);
        log.info("课程回答 questionId={} answerId={} userId={}", questionId, answer.getId(), userId);
        return answer.getId();
    }

    /** 标记已解决：只有提问人和课程讲师能动 */
    @Transactional
    public void resolve(Long userId, Long questionId, boolean resolved) {
        CourseQuestion question = requireQuestion(questionId);
        Course course = courseMapper.findById(question.getCourseId());
        if (course == null) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!canResolve(userId, course, question)) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        questionMapper.updateResolved(questionId, resolved);
        log.info("问题标记 resolved={} questionId={} userId={}", resolved, questionId, userId);
    }

    private boolean canResolve(Long userId, Course course, CourseQuestion question) {
        if (question.getUserId().equals(userId)) {
            return true;
        }
        TeacherProfile teacher = profileMapper.findById(course.getTeacherId());
        return teacher != null && teacher.getUserId().equals(userId);
    }

    private CourseQuestion requireQuestion(Long questionId) {
        CourseQuestion question = questionMapper.findById(questionId);
        if (question == null) {
            throw BizException.of(ErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    private void requirePublishedCourse(Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
    }
}
