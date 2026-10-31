package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.QaDtos;
import com.wherelee.edu.message.MessageService;
import com.wherelee.edu.security.UserContext;
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
    private final MessageService messageService;

    @Transactional
    public Long ask(Long userId, Long courseId, QaDtos.AskRequest request) {
        Course course = requirePublishedCourse(courseId);
        CourseQuestion question = new CourseQuestion();
        question.setCourseId(courseId);
        question.setUserId(userId);
        question.setContent(request.content().trim());
        questionMapper.insert(question);
        log.info("课程提问 courseId={} questionId={} userId={}", courseId, question.getId(), userId);

        // 提问产生了「等回答」的状态，必须有人能接住。先告知讲师，学员之间靠列表和通知
        TeacherProfile teacher = profileMapper.findById(course.getTeacherId());
        if (teacher != null && !teacher.getUserId().equals(userId)) {
            messageService.notifyAfterCommit(teacher.getUserId(),
                    "你的课程有新提问",
                    "《" + course.getTitle() + "》有学员提问：" + abbreviate(question.getContent(), 60),
                    "/courses/" + courseId + "?question=" + question.getId());
        }
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
        // 匿名访客可以读问答，但不需要知道每个提问人/回答人的内部 userId，
        // 那是登录后「这是不是我提的」才用的信息
        boolean anonymous = UserContext.get() == null;
        List<QaDtos.QuestionVO> records = questionMapper
                .findPageByCourse(courseId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new QaDtos.QuestionVO(row.id(), anonymous ? null : row.userId(), row.nickname(),
                        row.content(), Boolean.TRUE.equals(row.resolved()), row.answerCount(),
                        row.createdAt()))
                .toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    public QaDtos.QuestionDetailVO detail(Long questionId) {
        CourseQuestion question = requireQuestion(questionId);
        requirePublishedCourse(question.getCourseId());

        User asker = userMapper.findById(question.getUserId());
        boolean anonymous = UserContext.get() == null;
        List<QaDtos.AnswerVO> answers = answerMapper.findByQuestionId(questionId).stream()
                .map(row -> new QaDtos.AnswerVO(row.id(), anonymous ? null : row.userId(), row.nickname(),
                        row.content(), row.createdAt()))
                .toList();

        return new QaDtos.QuestionDetailVO(
                question.getId(),
                question.getCourseId(),
                anonymous ? null : question.getUserId(),
                asker == null ? null : asker.getNickname(),
                question.getContent(),
                Boolean.TRUE.equals(question.getResolved()),
                question.getCreatedAt(),
                answers);
    }

    @Transactional
    public Long answer(Long userId, Long questionId, QaDtos.AnswerRequest request) {
        CourseQuestion question = requireQuestion(questionId);
        Course course = requirePublishedCourse(question.getCourseId());

        // 已解决的问题不再堆回答：状态要唯一，想补充就另开一问
        if (Boolean.TRUE.equals(question.getResolved())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "该问题已标记为已解决，如需补充请另开一问");
        }

        CourseAnswer answer = new CourseAnswer();
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setContent(request.content().trim());
        answerMapper.insert(answer);
        log.info("课程回答 questionId={} answerId={} userId={}", questionId, answer.getId(), userId);

        // 提问人在等这个结果，必须告诉他。自己答自己不通知
        if (!question.getUserId().equals(userId)) {
            messageService.notifyAfterCommit(question.getUserId(),
                    "你的提问有人回答了",
                    abbreviate(question.getContent(), 40) + " → " + abbreviate(answer.getContent(), 60),
                    "/courses/" + course.getId() + "?question=" + questionId);
        }
        return answer.getId();
    }

    /** 撤回自己的提问。回答跟着问题一起删，不留下孤儿。 */
    @Transactional
    public void withdraw(Long userId, Long questionId) {
        CourseQuestion question = requireQuestion(questionId);
        if (!question.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        answerMapper.deleteByQuestion(questionId);
        questionMapper.deleteById(questionId);
        // 问题没了，之前发出去的「有人回答了你」就不能再指向它，否则点进去是空页面
        messageService.clearLinkForQuestion(question.getCourseId(), questionId);
        log.info("撤回提问 questionId={} userId={}", questionId, userId);
    }

    /** 我的提问：带课程名和回答数，不用进到课程里逐条翻 */
    public PageResult<QaDtos.MyQuestionVO> myQuestions(Long userId, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = questionMapper.countByUser(userId);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<QaDtos.MyQuestionVO> records = questionMapper
                .findPageByUser(userId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new QaDtos.MyQuestionVO(row.id(), row.courseId(), row.courseTitle(),
                        row.content(), Boolean.TRUE.equals(row.resolved()), row.answerCount(), row.createdAt()))
                .toList();
        return PageResult.of(records, total, safePage, safeSize);
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

    private Course requirePublishedCourse(Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    /** 通知正文里的内容缩略，避免一条站内信装下一整段问答 */
    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
