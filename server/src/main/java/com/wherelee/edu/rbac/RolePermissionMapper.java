package com.wherelee.edu.rbac;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper {

    @Select("SELECT permission_id FROM t_role_permission WHERE role_id = #{roleId}")
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 给角色重新配权限时整表覆盖：先清空该角色的所有关联，再批量插入。
     * 调用方必须放在一个事务里，否则中间态会出现「权限被清空但还没写回」。
     */
    @Delete("DELETE FROM t_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    int batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    @Select("SELECT COUNT(*) FROM t_role_permission WHERE permission_id = #{permissionId}")
    long countByPermission(@Param("permissionId") Long permissionId);
}
