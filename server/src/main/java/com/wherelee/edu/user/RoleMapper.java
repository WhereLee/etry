package com.wherelee.edu.user;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("SELECT id, code, name, description FROM t_role WHERE code = #{code}")
    Role findByCode(@Param("code") String code);

    @Select("SELECT id, code, name, description FROM t_role ORDER BY id")
    List<Role> findAll();

    /**
     * 查某人的角色编码。走 t_user_role 主键的最左列（user_id），再回表 t_role 取 code。
     */
    @Select("SELECT r.code FROM t_role r "
            + "JOIN t_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} ORDER BY r.id")
    List<String> findCodesByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO t_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int bindRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM t_user_role WHERE user_id = #{userId}")
    int unbindAll(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_user_role WHERE role_id = #{roleId}")
    long countUsersByRole(@Param("roleId") Long roleId);

    /** 批量取多个用户的角色，避免列表页 N+1，SQL 在 RoleMapper.xml */
    List<UserRoleRow> findRolesByUserIds(@Param("userIds") List<Long> userIds);
}
