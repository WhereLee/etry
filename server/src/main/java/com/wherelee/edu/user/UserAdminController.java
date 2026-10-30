package com.wherelee.edu.user;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.user.dto.AssignRolesRequest;
import com.wherelee.edu.user.dto.ChangeStatusRequest;
import com.wherelee.edu.user.dto.UserListItemVO;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @RequirePermission("user:manage")
    public ApiResponse<PageResult<UserListItemVO>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(userAdminService.page(keyword, status, page, size));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("user:manage")
    @AuditLog(action = "USER_STATUS_CHANGE", targetType = "USER", targetIdIndex = 0)
    public ApiResponse<Void> changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody ChangeStatusRequest request,
                                          @CurrentUser Long operatorId) {
        userAdminService.changeStatus(id, request.status(), operatorId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/roles")
    @RequirePermission("user:manage")
    @AuditLog(action = "USER_ROLE_ASSIGN", targetType = "USER", targetIdIndex = 0)
    public ApiResponse<Void> assignRoles(@PathVariable Long id,
                                         @Valid @RequestBody AssignRolesRequest request) {
        userAdminService.assignRoles(id, request.roleIds());
        return ApiResponse.ok();
    }
}
