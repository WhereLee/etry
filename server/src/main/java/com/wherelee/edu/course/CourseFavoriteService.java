package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程收藏。
 *
 * 收藏本身是幂等的开关：重复收藏不报错、不产生第二条；
 * 取消不存在的收藏也当成功，前端不用先查状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFavoriteService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final CourseFavoriteMapper favoriteMapper;
    private final CourseMapper courseMapper;
    private final CourseAssembler assembler;

    @Transactional
    public void add(Long userId, Long courseId) {
        Course course = courseMapper.findById(courseId);
        if (course == null || !Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw BizException.of(ErrorCode.COURSE_NOT_FOUND);
        }
        favoriteMapper.insertIgnore(userId, courseId);
        log.info("收藏课程 courseId={} userId={}", courseId, userId);
    }

    @Transactional
    public void remove(Long userId, Long courseId) {
        favoriteMapper.delete(userId, courseId);
    }

    public boolean isFavorited(Long userId, Long courseId) {
        return userId != null && favoriteMapper.count(userId, courseId) > 0;
    }

    /**
     * 我的收藏。下架的课程仍然列出（收藏记录不因下架消失），
     * status 字段会告诉前端「已下架」，点进去会拿到 40408。
     */
    public PageResult<CourseDtos.CourseSummaryVO> page(Long userId, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = favoriteMapper.countByUser(userId);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<CourseDtos.CourseSummaryVO> records = assembler.summaries(
                favoriteMapper.findCoursesByUser(userId, (safePage - 1) * safeSize, safeSize));
        return PageResult.of(records, total, safePage, safeSize);
    }
}
