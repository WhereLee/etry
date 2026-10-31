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

import java.util.List;

/**
 * 前台：课程列表、详情、播放地址。
 *
 * 目前没有购买功能，所以「登录即可看」——所有已上架课程对登录用户开放。
 * 播放地址是预签名 GET，前端拿到就能播，拿不到对象键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoursePublicService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final CourseMapper courseMapper;
    private final CourseLessonMapper lessonMapper;
    private final CourseAssembler assembler;
    private final FileStorage fileStorage;

    public PageResult<CourseDtos.CourseSummaryVO> page(Long subjectId, String keyword, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeKeyword = Keywords.escapeLike(keyword);

        long total = courseMapper.countPublic(subjectId, safeKeyword);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<CourseDtos.CourseSummaryVO> records = courseMapper
                .findPublicPage(subjectId, safeKeyword, (safePage - 1) * safeSize, safeSize)
                .stream().map(assembler::summary).toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    /** 前台详情：非上架状态一律按不存在处理，不泄露「有这门课但在草稿里」 */
    public CourseDtos.CourseDetailVO detail(Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        return assembler.detail(course);
    }

    /**
     * 播放地址。两个前提：课时有视频，且它所属的课程仍在上架状态。
     * 课程下架后，之前拿到的播放链接最多再活 2 小时（预签名有效期）。
     */
    public CourseDtos.PlayVO play(Long lessonId) {
        CourseLesson lesson = lessonMapper.findById(lessonId);
        if (lesson == null || lesson.getVideoKey() == null || lesson.getVideoKey().isBlank()) {
            throw BizException.of(ErrorCode.LESSON_NOT_FOUND);
        }
        Course course = courseMapper.findById(lesson.getCourseId());
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        String playUrl = fileStorage.url(lesson.getVideoKey());
        log.info("获取播放地址 lessonId={} courseId={}", lessonId, course.getId());
        return new CourseDtos.PlayVO(lesson.getId(), lesson.getTitle(), playUrl);
    }
}
