package com.wherelee.edu.course;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.ReviewDtos;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程评价。登录即可用，不分角色——老师和学员都可能学过别人的课。
 */
@RestController
@RequestMapping("/api/courses/{courseId}/reviews")
@RequiredArgsConstructor
public class CourseReviewController {

    private final CourseReviewService reviewService;

    /** 提交或修改自己的评价（一人一课只有一条） */
    @PostMapping
    public ApiResponse<Void> submit(@CurrentUser Long userId,
                                    @PathVariable Long courseId,
                                    @Valid @RequestBody ReviewDtos.SubmitRequest request) {
        reviewService.submit(userId, courseId, request);
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<PageResult<ReviewDtos.ReviewVO>> page(@PathVariable Long courseId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reviewService.page(courseId, page, size));
    }

    /** 自己在课程详情里回显用；没评过返回 null */
    @GetMapping("/mine")
    public ApiResponse<ReviewDtos.MyReviewVO> mine(@CurrentUser Long userId,
                                                   @PathVariable Long courseId) {
        return ApiResponse.ok(reviewService.mine(userId, courseId));
    }

    @DeleteMapping("/mine")
    public ApiResponse<Void> remove(@CurrentUser Long userId, @PathVariable Long courseId) {
        reviewService.remove(userId, courseId);
        return ApiResponse.ok();
    }
}
