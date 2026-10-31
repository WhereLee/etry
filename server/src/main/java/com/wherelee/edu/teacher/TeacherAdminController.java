package com.wherelee.edu.teacher;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.teacher.dto.TeacherProfileVO;
import com.wherelee.edu.teacher.dto.TeacherReviewRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/teachers")
@RequiredArgsConstructor
public class TeacherAdminController {

    private final TeacherService teacherService;

    @GetMapping
    @RequirePermission("teacher:review")
    public ApiResponse<PageResult<TeacherProfileVO>> page(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(teacherService.page(status, page, size));
    }

    /**
     * 审核通过会在同一事务里给用户加 TEACHER 角色，所以这里必须记录审计：
     * 「谁把谁提升成了讲师」是要能查的。
     */
    @PutMapping("/{id}/review")
    @RequirePermission("teacher:review")
    @AuditLog(action = "TEACHER_REVIEW", targetType = "TEACHER", targetIdIndex = 0)
    public ApiResponse<Void> review(@PathVariable Long id,
                                    @Valid @RequestBody TeacherReviewRequest request,
                                    @CurrentUser Long operatorId) {
        teacherService.review(id, Boolean.TRUE.equals(request.approved()), request.rejectReason(), operatorId);
        return ApiResponse.ok();
    }

    /**
     * 讲师离职归档：档案保留（审计还要对得上人），只解绑 TEACHER 角色，账号不动。
     * 不物理删用户——删了审计里的 user_id 就对不上具体是谁了。
     */
    @PutMapping("/{id}/archive")
    @RequirePermission("teacher:review")
    @AuditLog(action = "TEACHER_ARCHIVE", targetType = "TEACHER", targetIdIndex = 0)
    public ApiResponse<Void> archive(@PathVariable Long id) {
        teacherService.changeArchiveStatus(id, true);
        return ApiResponse.ok();
    }

    /** 复职：把归档的档案恢复成在职，并重新授予 TEACHER 角色 */
    @PutMapping("/{id}/restore")
    @RequirePermission("teacher:review")
    @AuditLog(action = "TEACHER_RESTORE", targetType = "TEACHER", targetIdIndex = 0)
    public ApiResponse<Void> restore(@PathVariable Long id) {
        teacherService.changeArchiveStatus(id, false);
        return ApiResponse.ok();
    }
}
