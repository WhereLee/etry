package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.ReviewDtos;
import com.wherelee.edu.learning.LearningProgressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 课程评价。
 *
 * 两条规则：
 *   1. 学过才能评——靠学习记录判断，不是靠「有没有买」（免费平台没有购买）
 *   2. 一人一课一条——唯一键兜底，重复提交是「改评价」，不能刷分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseReviewService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final CourseReviewMapper reviewMapper;
    private final CourseMapper courseMapper;
    private final LearningProgressMapper progressMapper;

    @Transactional
    public void submit(Long userId, Long courseId, ReviewDtos.SubmitRequest request) {
        requirePublishedCourse(courseId);
        if (progressMapper.countCompletedByUserAndCourse(userId, courseId) == 0) {
            // 只看「有无进度」等于没门槛：上报一条 position=0 就能评
            throw new BizException(ErrorCode.INVALID_PARAM, "完成至少一个课时后才能评价");
        }
        String content = StringUtils.hasText(request.content()) ? request.content().trim() : null;
        reviewMapper.upsert(courseId, userId, request.rating(), content);
        log.info("课程评价 courseId={} userId={} rating={}", courseId, userId, request.rating());
    }

    public PageResult<ReviewDtos.ReviewVO> page(Long courseId, int page, int size) {
        requirePublishedCourse(courseId);
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = reviewMapper.countByCourse(courseId);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<ReviewDtos.ReviewVO> records = reviewMapper
                .findPageByCourse(courseId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new ReviewDtos.ReviewVO(row.id(), row.userId(), row.nickname(),
                        row.rating(), row.content(), row.createdAt()))
                .toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    /** 回显自己打过的分；没评过返回 null */
    public ReviewDtos.MyReviewVO mine(Long userId, Long courseId) {
        CourseReview review = reviewMapper.findByUserAndCourse(userId, courseId);
        if (review == null) {
            return null;
        }
        return new ReviewDtos.MyReviewVO(review.getRating(), review.getContent(), review.getUpdatedAt());
    }

    @Transactional
    public void remove(Long userId, Long courseId) {
        reviewMapper.deleteByUserAndCourse(userId, courseId);
        log.info("课程评价删除 courseId={} userId={}", courseId, userId);
    }

    private void requirePublishedCourse(Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
    }
}
