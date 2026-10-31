package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.Keywords;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员侧：审核课程、上下架。
 *
 * 状态流转只走这里和老师侧的 submit：
 *   DRAFT/REJECTED --提交--> PENDING --通过--> PUBLISHED
 *                                   --驳回--> REJECTED
 *   PUBLISHED --下架--> OFFLINE --重新上架--> PUBLISHED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseAdminService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final CourseMapper courseMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseAssembler assembler;
    private final FileStorage fileStorage;

    public PageResult<CourseDtos.CourseSummaryVO> page(String status, String keyword, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeKeyword = Keywords.escapeLike(keyword);

        long total = courseMapper.countAdmin(status, safeKeyword);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<CourseDtos.CourseSummaryVO> records = assembler.summaries(
                courseMapper.findAdminPage(status, safeKeyword, (safePage - 1) * safeSize, safeSize));
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Transactional
    public void review(Long courseId, boolean approved, String rejectReason) {
        Course course = requireCourse(courseId);
        if (!Course.STATUS_PENDING.equals(course.getStatus())) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有待审核的课程可以审核");
        }
        if (approved) {
            // 首次上架才写发布时间，重新上架不改它
            LocalDateTime publishedAt = course.getPublishedAt() == null
                    ? LocalDateTime.now() : course.getPublishedAt();
            courseMapper.updateStatus(courseId, Course.STATUS_PUBLISHED, null, publishedAt);
            log.info("课程审核通过 courseId={}", courseId);
        } else {
            String reason = rejectReason == null || rejectReason.isBlank()
                    ? "资料不完整" : rejectReason.trim();
            courseMapper.updateStatus(courseId, Course.STATUS_REJECTED, reason, course.getPublishedAt());
            log.info("课程驳回 courseId={} reason={}", courseId, reason);
        }
    }

    @Transactional
    public void offline(Long courseId) {
        Course course = requireCourse(courseId);
        if (!Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有已上架的课程可以下架");
        }
        courseMapper.updateStatus(courseId, Course.STATUS_OFFLINE, null, course.getPublishedAt());
        log.info("课程下架 courseId={}", courseId);
    }

    @Transactional
    public void publish(Long courseId) {
        Course course = requireCourse(courseId);
        if (!Course.STATUS_OFFLINE.equals(course.getStatus())) {
            throw new BizException(ErrorCode.COURSE_STATUS_INVALID, "只有已下架的课程可以重新上架");
        }
        courseMapper.updateStatus(courseId, Course.STATUS_PUBLISHED, null, course.getPublishedAt());
        log.info("课程重新上架 courseId={}", courseId);
    }

    /**
     * 审核用的详情：比前台多一层——不限状态，草稿和待审的也能看，
     * 否则审核人就只能看个标题就点通过了。
     */
    public CourseDtos.CourseDetailVO detail(Long courseId) {
        return assembler.detail(requireCourse(courseId));
    }

    /**
     * 审核用的视频预览：不要求课程已上架。
     * 前台那个 play 接口要求 PUBLISHED，待审的课根本调不出来。
     */
    public CourseDtos.PlayVO previewPlay(Long lessonId) {
        CourseLesson lesson = lessonMapper.findById(lessonId);
        if (lesson == null || !StringUtils.hasText(lesson.getVideoKey())) {
            throw BizException.of(ErrorCode.LESSON_NOT_FOUND);
        }
        return new CourseDtos.PlayVO(lesson.getId(), lesson.getTitle(),
                fileStorage.url(lesson.getVideoKey()));
    }

    private Course requireCourse(Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }
}
