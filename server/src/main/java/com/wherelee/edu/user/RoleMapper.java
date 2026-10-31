package com.wherelee.edu.user;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Insert("INSERT INTO t_role (code, name, description) VALUES (#{code}, #{name}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Role role);

    /** 只允许改名称和描述：code 在代码里被引用，改了会让按 code 判断的逻辑失效 */
    @Update("UPDATE t_role SET name = #{name}, description = #{description} WHERE id = #{id}")
    int update(Role role);

    @Delete("DELETE FROM t_role WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM t_user_role WHERE user_id = #{userId}")
    int unbindAll(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_user_role WHERE role_id = #{roleId}")
    long countUsersByRole(@Param("roleId") Long roleId);

    /**
     * 加锁读出某个角色下所有可用用户的 ID。
     * 「先查后改」在并发下会被绕过（两人同时撤销对方，各自都读到 2 个），
     * 用 FOR UPDATE 把行锁住，让两个事务串行，第二个能看到第一个的结果。
     * 调用方必须在事务内。
     */
    @Select("SELECT u.id FROM t_user u "
            + "JOIN t_user_role ur ON u.id = ur.user_id "
            + "JOIN t_role r ON r.id = ur.role_id "
            + "WHERE r.code = #{roleCode} AND u.status = 'ACTIVE' FOR UPDATE")
    List<Long> lockActiveUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /** 批量取多个用户的角色，避免列表页 N+1，SQL 在 RoleMapper.xml */
    List<UserRoleRow> findRolesByUserIds(@Param("userIds") List<Long> userIds);
}
