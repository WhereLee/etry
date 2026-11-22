package com.wherelee.material.modules.identity.seed;

import com.wherelee.material.modules.identity.rbac.RbacRepository;
import com.wherelee.material.modules.platform.seed.SeedContext;
import com.wherelee.material.modules.platform.seed.SeedContributor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RBAC 种子：权限点登记、角色-权限绑定、演示账号授角色。全部幂等，重复启动不重复写入。
 */
@Component
public class RbacSeedContributor implements SeedContributor {

    private static final List<String[]> PERMISSIONS = List.of(
            new String[]{"material:read", "资料查询", "material"},
            new String[]{"material:create", "资料新建", "material"},
            new String[]{"material:update", "资料编辑", "material"},
            new String[]{"material:sale", "资料上下架", "material"},
            new String[]{"order:read", "订单查询", "commerce"},
            new String[]{"refund:operate", "退款处理", "commerce"},
            new String[]{"recon:read", "对账查询", "commerce"},
            new String[]{"recon:operate", "对账处理", "commerce"},
            new String[]{"user:read", "用户查询", "identity"},
            new String[]{"user:manage", "用户与角色管理", "identity"},
            new String[]{"audit:read", "审计日志查询", "platform"}
    );

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "OPERATOR", List.of("material:read", "material:create", "material:update", "material:sale"),
            "CUSTOMER_SERVICE", List.of("order:read", "refund:operate", "user:read"),
            "FINANCE", List.of("order:read", "refund:operate", "recon:read", "recon:operate"),
            "SUPER_ADMIN", PERMISSIONS.stream().map(p -> p[0]).toList()
    );

    private final RbacRepository rbacRepository;

    public RbacSeedContributor(RbacRepository rbacRepository) {
        this.rbacRepository = rbacRepository;
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public void contribute(SeedContext context) {
        for (String[] permission : PERMISSIONS) {
            rbacRepository.upsertPermission(permission[0], permission[1], permission[2]);
        }
        ROLE_PERMISSIONS.forEach((role, codes) ->
                codes.forEach(code -> rbacRepository.linkRolePermission(role, code)));
        rbacRepository.grantRole(context.userId("admin"), "SUPER_ADMIN", null);
        rbacRepository.grantRole(context.userId("teacher"), "OPERATOR", null);
        rbacRepository.grantRole(context.userId("student"), "BUYER", null);
    }
}