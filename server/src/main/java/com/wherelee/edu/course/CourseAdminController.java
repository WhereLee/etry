package com.wherelee.edu.course;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.course.dto.CourseDtos;
import com.wherelee.edu.rbac.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员侧：课程审核与上下架。
 *
 * 状态机只在这里和老师侧的 submit 上转，
 * 所以「谁把课放出去了」永远能在审计里查到。
 */
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class CourseAdminController {

    private final CourseAdminService courseAdminService;

    @GetMapping
    @RequirePermission("course:review")
    public ApiResponse<PageResult<CourseDtos.CourseSummaryVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(courseAdminService.page(status, keyword, page, size));
    }

    @PutMapping("/{id}/review")
    @RequirePermission("course:review")
    @AuditLog(action = "COURSE_REVIEW", targetType = "COURSE", targetIdIndex = 0)
    public ApiResponse<Void> review(@PathVariable Long id,
                                    @Valid @RequestBody CourseDtos.ReviewRequest request) {
        courseAdminService.review(id, Boolean.TRUE.equals(request.approved()), request.rejectReason());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/offline")
    @RequirePermission("course:review")
    @AuditLog(action = "COURSE_OFFLINE", targetType = "COURSE", targetIdIndex = 0)
    public ApiResponse<Void> offline(@PathVariable Long id) {
        courseAdminService.offline(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("course:review")
    @AuditLog(action = "COURSE_PUBLISH", targetType = "COURSE", targetIdIndex = 0)
    public ApiResponse<Void> publish(@PathVariable Long id) {
        courseAdminService.publish(id);
        return ApiResponse.ok();
    }
}
