package com.wherelee.edu.course;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.rbac.RequirePermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员侧评价治理（业务规则 §四 4.6）。
 *
 * 只有隐藏，没有编辑、没有物理删：
 * 编辑等于管理员代写学员发言，物理删让审计对不上账。
 */
@RestController
@RequestMapping("/api/admin/reviews")
public class CourseReviewAdminController {

    private final CourseReviewService reviewService;

    public CourseReviewAdminController(CourseReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PutMapping("/{id}/hide")
    @RequirePermission("course:review")
    @AuditLog(action = "REVIEW_HIDE", targetType = "REVIEW", targetIdIndex = 0)
    public ApiResponse<Void> hide(@PathVariable Long id) {
        reviewService.hide(id);
        return ApiResponse.ok();
    }
}
