package com.wherelee.edu.rbac;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper {

    /**
     * 三表 join 取某人的全部权限编码。
     * 中间经过 t_role_permission，一个用户多角色时用 DISTINCT 去重。
     * 每次请求查一次库：权限变更要立即生效，不能缓存在 token 里等过期。
     */
    @Select("SELECT DISTINCT p.code FROM t_permission p "
            + "JOIN t_role_permission rp ON p.id = rp.permission_id "
            + "JOIN t_user_role ur ON rp.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId}")
    List<String> findCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.code FROM t_permission p "
            + "JOIN t_role_permission rp ON p.id = rp.permission_id "
            + "WHERE rp.role_id = #{roleId}")
    List<String> findCodesByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT id, code, name, module FROM t_permission ORDER BY module, id")
    List<Permission> findAll();
}
