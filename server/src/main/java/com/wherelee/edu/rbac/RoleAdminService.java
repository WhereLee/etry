package com.wherelee.edu.rbac;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.rbac.dto.PermissionVO;
import com.wherelee.edu.rbac.dto.RoleVO;
import com.wherelee.edu.user.Role;
import com.wherelee.edu.user.RoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public List<RoleVO> listRoles() {
        return roleMapper.findAll().stream()
                .map(r -> new RoleVO(r.getId(), r.getCode(), r.getName(), r.getDescription()))
                .toList();
    }

    public List<PermissionVO> listPermissions() {
        return permissionMapper.findAll().stream()
                .map(p -> new PermissionVO(p.getId(), p.getCode(), p.getName(), p.getModule()))
                .toList();
    }

    public List<String> listRolePermissions(Long roleId) {
        requireRole(roleId);
        return permissionMapper.findCodesByRoleId(roleId);
    }

    /**
     * 整体覆盖某个角色的权限。
     * 在一个事务里「先删后插」：中途失败会回滚，不会留下权限被清空的中间态。
     *
     * 管理员角色必须保留 role:manage：改完这一次如果连权限管理都没了，
     * 就再也没人能进后台把权限改回来，只能改库。
     */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = requireRole(roleId);

        List<Permission> allPermissions = permissionMapper.findAll();
        Set<Long> allPermissionIds = allPermissions.stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
        Set<Long> requested = new HashSet<>(permissionIds);
        for (Long id : requested) {
            if (!allPermissionIds.contains(id)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "权限不存在: " + id);
            }
        }

        if (Role.CODE_ADMIN.equals(role.getCode())) {
            Long roleManageId = allPermissions.stream()
                    .filter(p -> "role:manage".equals(p.getCode()))
                    .map(Permission::getId)
                    .findFirst()
                    .orElse(null);
            if (roleManageId == null || !requested.contains(roleManageId)) {
                throw new BizException(ErrorCode.INVALID_PARAM,
                        "管理员角色必须保留 role:manage，否则将无人能再修改权限");
            }
        }

        rolePermissionMapper.deleteByRoleId(roleId);
        if (!requested.isEmpty()) {
            rolePermissionMapper.batchInsert(roleId, List.copyOf(requested));
        }
        log.info("角色权限变更 roleId={} permissions={}", roleId, requested);
    }

    private Role requireRole(Long roleId) {
        return roleMapper.findAll().stream()
                .filter(r -> r.getId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> BizException.of(ErrorCode.ROLE_NOT_FOUND));
    }
}
