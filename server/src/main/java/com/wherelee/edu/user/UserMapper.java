package com.wherelee.edu.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    String COLUMNS = "id, username, password_hash, nickname, avatar_key, bio, status, token_version, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT " + COLUMNS + " FROM t_user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Insert("INSERT INTO t_user (username, password_hash, nickname, status, token_version) "
            + "VALUES (#{username}, #{passwordHash}, #{nickname}, #{status}, #{tokenVersion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /**
     * 改密码并把令牌版本 +1。走条件更新，避免「先查后改」的竞态。
     */
    @Update("UPDATE t_user SET password_hash = #{passwordHash}, token_version = token_version + 1 "
            + "WHERE id = #{id}")
    int updatePasswordAndBumpVersion(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /** 管理端分页查询，动态条件在 UserMapper.xml */
    List<User> findPage(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword, @Param("status") String status);

    @Update("UPDATE t_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 令牌版本 +1，使该用户已签发的 token 全部失效（强制下线、禁用时用） */
    @Update("UPDATE t_user SET token_version = token_version + 1 WHERE id = #{id}")
    int bumpTokenVersion(@Param("id") Long id);

    /** 用户自助修改昵称与简介（不改账号、角色、状态） */
    @Update("UPDATE t_user SET nickname = #{nickname}, bio = #{bio} WHERE id = #{id}")
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("bio") String bio);

    @Update("UPDATE t_user SET avatar_key = #{avatarKey} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarKey") String avatarKey);

    /** 按角色编码取用户 ID 列表，供按角色群发站内信 */
    @Select("SELECT ur.user_id FROM t_user_role ur JOIN t_role r ON r.id = ur.role_id "
            + "WHERE r.code = #{roleCode}")
    List<Long> findIdsByRoleCode(@Param("roleCode") String roleCode);

    /** 一次查出这批 ID 里真实存在的，用于群发前的收件人校验（避免逐个 findById） */
    List<Long> findExistingIds(@Param("ids") List<Long> ids);
}
