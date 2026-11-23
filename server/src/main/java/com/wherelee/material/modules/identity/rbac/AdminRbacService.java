package com.wherelee.material.modules.identity.rbac;

import com.wherelee.material.modules.identity.rbac.AdminRbacDtos.RoleView;
import com.wherelee.material.modules.identity.user.UserRepository;
import com.wherelee.material.modules.platform.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理：角色目录回显、用户角色回显与整体替换。自我改角色禁止，沿用账号状态自我保护口径。
 */
@Service
public class AdminRbacService {

    private final RbacRepository rbacRepository;
    private final UserRepository userRepository;

    public AdminRbacService(RbacRepository rbacRepository, UserRepository userRepository) {
        this.rbacRepository = rbacRepository;
        this.userRepository = userRepository;
    }

    public List<RoleView> roles() {
        return rbacRepository.listRoles().stream()
                .map(role -> new RoleView(role.code(), role.name(), role.dataScope(), role.remark(),
                        rbacRepository.findPermissionCodesByRole(role.code())))
                .toList();
    }

    public List<String> userRoles(Long userId) {
        requireUser(userId);
        return rbacRepository.findRoleCodesByUser(userId);
    }

    @Transactional
    public List<String> assignRoles(Long operatorId, Long userId, List<String> roleCodes) {
        if (operatorId.equals(userId)) {
            throw BizException.badRequest("不能修改自己的角色");
        }
        requireUser(userId);
        List<String> codes = roleCodes.stream().distinct().toList();
        for (String code : codes) {
            rbacRepository.findRoleByCode(code)
                    .orElseThrow(() -> BizException.badRequest("角色不存在：" + code));
        }
        rbacRepository.deleteUserRoles(userId);
        codes.forEach(code -> rbacRepository.grantRole(userId, code, operatorId));
        return rbacRepository.findRoleCodesByUser(userId);
    }

    private void requireUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }
}