package com.wherelee.edu.user;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.security.CurrentUser;
import com.wherelee.edu.user.dto.AdminResetPasswordRequest;
import com.wherelee.edu.user.dto.AssignRolesRequest;
import com.wherelee.edu.user.dto.ChangeStatusRequest;
import com.wherelee.edu.user.dto.CreateUserRequest;
import com.wherelee.edu.user.dto.UserListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * 管理员开号：建账号 + 发角色 +（讲师）同时建档。
     * 新号的初始密码由管理员掌握，所以对方首次登录必须改密。
     */
    @PostMapping
    @RequirePermission("user:manage")
    @AuditLog(action = "USER_CREATE", targetType = "USER")
    public ApiResponse<Long> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userAdminService.createUser(request));
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

    /** 管理员重置他人密码：不需要旧密码，重置后该用户所有设备下线，且下次登录必须再改一次 */
    @PutMapping("/{id}/password")
    @RequirePermission("user:manage")
    @AuditLog(action = "USER_PASSWORD_RESET", targetType = "USER", targetIdIndex = 0)
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody AdminResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.newPassword());
        return ApiResponse.ok();
    }

    /** 强制下线：只作废 token，账号本身不动 */
    @PutMapping("/{id}/kick")
    @RequirePermission("user:manage")
    @AuditLog(action = "USER_KICK", targetType = "USER", targetIdIndex = 0)
    public ApiResponse<Void> kick(@PathVariable Long id) {
        userAdminService.kick(id);
        return ApiResponse.ok();
    }
}
