package com.wherelee.material.modules.identity.rbac;

import com.wherelee.material.modules.identity.rbac.AdminRbacDtos.AssignRolesRequest;
import com.wherelee.material.modules.identity.rbac.AdminRbacDtos.RoleView;
import com.wherelee.material.modules.platform.aop.AdminAudit;
import com.wherelee.material.modules.platform.aop.Measured;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 超级管理员角色权限管理接口（PRD §2 超级管理员、§4 权限点鉴权 + 写操作审计）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminRbacController {

    private final AdminRbacService service;

    public AdminRbacController(AdminRbacService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    @Measured("admin.rbac.roles")
    public List<RoleView> roles() {
        return service.roles();
    }

    @GetMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    @Measured("admin.rbac.userRoles")
    public List<String> userRoles(@PathVariable Long id) {
        return service.userRoles(id);
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    @Measured("admin.rbac.assignRoles")
    @AdminAudit(action = "USER_ROLES_ASSIGN", targetType = "USER", targetIdArg = 1)
    public List<String> assignRoles(@RequestAttribute("userId") Long operatorId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        return service.assignRoles(operatorId, id, request.roleCodes());
    }
}