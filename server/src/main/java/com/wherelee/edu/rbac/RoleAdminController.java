package com.wherelee.edu.rbac;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.rbac.dto.AssignPermissionsRequest;
import com.wherelee.edu.rbac.dto.CreateRoleRequest;
import com.wherelee.edu.rbac.dto.PermissionVO;
import com.wherelee.edu.rbac.dto.RoleVO;
import com.wherelee.edu.rbac.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    @GetMapping("/roles")
    @RequirePermission("role:manage")
    public ApiResponse<List<RoleVO>> listRoles() {
        return ApiResponse.ok(roleAdminService.listRoles());
    }

    /** 新建角色，用于「助教」「教务」这类机构内部身份 */
    @PostMapping("/roles")
    @RequirePermission("role:manage")
    @AuditLog(action = "ROLE_CREATE", targetType = "ROLE")
    public ApiResponse<RoleVO> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(roleAdminService.createRole(request));
    }

    /** 只改名称与描述，code 不可改 */
    @PutMapping("/roles/{id}")
    @RequirePermission("role:manage")
    @AuditLog(action = "ROLE_UPDATE", targetType = "ROLE", targetIdIndex = 0)
    public ApiResponse<Void> updateRole(@PathVariable Long id,
                                        @Valid @RequestBody UpdateRoleRequest request) {
        roleAdminService.updateRole(id, request);
        return ApiResponse.ok();
    }

    /** 内置角色、仍有人使用的角色不能被删 */
    @DeleteMapping("/roles/{id}")
    @RequirePermission("role:manage")
    @AuditLog(action = "ROLE_DELETE", targetType = "ROLE", targetIdIndex = 0)
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleAdminService.deleteRole(id);
        return ApiResponse.ok();
    }

    @GetMapping("/permissions")
    @RequirePermission("role:manage")
    public ApiResponse<List<PermissionVO>> listPermissions() {
        return ApiResponse.ok(roleAdminService.listPermissions());
    }

    @GetMapping("/roles/{id}/permissions")
    @RequirePermission("role:manage")
    public ApiResponse<List<String>> listRolePermissions(@PathVariable Long id) {
        return ApiResponse.ok(roleAdminService.listRolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePermission("role:manage")
    @AuditLog(action = "ROLE_PERMISSION_ASSIGN", targetType = "ROLE", targetIdIndex = 0)
    public ApiResponse<Void> assignPermissions(@PathVariable Long id,
                                               @Valid @RequestBody AssignPermissionsRequest request) {
        roleAdminService.assignPermissions(id, request.permissionIds());
        return ApiResponse.ok();
    }
}
