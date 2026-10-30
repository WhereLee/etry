package com.wherelee.edu.rbac;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.rbac.dto.AssignPermissionsRequest;
import com.wherelee.edu.rbac.dto.PermissionVO;
import com.wherelee.edu.rbac.dto.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
