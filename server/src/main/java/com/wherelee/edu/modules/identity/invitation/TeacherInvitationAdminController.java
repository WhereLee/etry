package com.wherelee.edu.modules.identity.invitation;

import com.wherelee.edu.modules.platform.aop.AdminAudit;
import com.wherelee.edu.modules.platform.aop.Idempotent;
import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RateLimit;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import com.wherelee.edu.modules.platform.common.PageResult;
import com.wherelee.edu.modules.identity.invitation.dto.TeacherInvitationDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/teacher-invitations")
public class TeacherInvitationAdminController {

    private final TeacherInvitationService service;

    public TeacherInvitationAdminController(TeacherInvitationService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRole("ADMIN")
    @RateLimit(key = "admin:teacherInvite", limit = 20, windowSeconds = 60)
    @Idempotent(key = "admin:teacherInvite", windowSeconds = 5)
    @Measured("admin.teacherInvitation.create")
    @AdminAudit(action = "TEACHER_INVITE_CREATE", targetType = "TEACHER_INVITATION",
            targetIdFromResult = true)
    public TeacherInvitationDtos.CreatedResponse create(
            @RequestAttribute("userId") Long adminId,
            @Valid @RequestBody TeacherInvitationDtos.CreateRequest request) {
        return service.create(request, adminId);
    }

    @GetMapping
    @RequireRole("ADMIN")
    @Measured("admin.teacherInvitation.page")
    public PageResult<TeacherInvitationVO> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.page(status, page, size);
    }

    @PutMapping("/{id}/revoke")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:teacherInviteRevoke", windowSeconds = 3)
    @Measured("admin.teacherInvitation.revoke")
    @AdminAudit(action = "TEACHER_INVITE_REVOKE", targetType = "TEACHER_INVITATION",
            targetIdArg = 0)
    public void revoke(@PathVariable Long id) {
        service.revoke(id);
    }
}
