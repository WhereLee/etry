package com.wherelee.edu.course.enrollment;

import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 加入 / 移出我的学习。
 *
 * 路径挂在 /api/courses 下但没有命中公开规则——公开规则只认 /api/courses 和
 * /api/courses/{纯数字}，带子路径的一律要求登录。这里正好需要登录。
 */
@RestController
@RequiredArgsConstructor
public class CourseEnrollmentController {

    private final CourseEnrollmentService enrollmentService;

    @PostMapping("/api/courses/{id}/enroll")
    public ApiResponse<Void> enroll(@CurrentUser Long userId, @PathVariable Long id) {
        enrollmentService.enroll(userId, id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/courses/{id}/enroll")
    public ApiResponse<Void> quit(@CurrentUser Long userId, @PathVariable Long id) {
        enrollmentService.quit(userId, id);
        return ApiResponse.ok();
    }
}
