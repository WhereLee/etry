package com.wherelee.edu.teacher;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.teacher.dto.CreateSubjectRequest;
import com.wherelee.edu.teacher.dto.SubjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    /** 学科列表对所有人可见：注册、讲师申请都要用 */
    @GetMapping("/api/subjects")
    public ApiResponse<List<SubjectVO>> list() {
        return ApiResponse.ok(subjectService.list());
    }

    @PostMapping("/api/admin/subjects")
    @RequirePermission("subject:manage")
    @AuditLog(action = "SUBJECT_CREATE", targetType = "SUBJECT")
    public ApiResponse<SubjectVO> create(@Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.ok(subjectService.create(request));
    }

    @DeleteMapping("/api/admin/subjects/{id}")
    @RequirePermission("subject:manage")
    @AuditLog(action = "SUBJECT_DELETE", targetType = "SUBJECT", targetIdIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ApiResponse.ok();
    }
}
