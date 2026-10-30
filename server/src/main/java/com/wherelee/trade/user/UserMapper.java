package com.wherelee.trade.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    String COLUMNS = "id, username, password_hash, nickname, role, token_version, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT " + COLUMNS + " FROM t_user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Insert("INSERT INTO t_user (username, password_hash, nickname, role, token_version) "
            + "VALUES (#{username}, #{passwordHash}, #{nickname}, #{role}, #{tokenVersion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /**
     * 改密码并把令牌版本 +1，使该用户此前签发的所有 token 失效。
     */
    @Update("UPDATE t_user SET password_hash = #{passwordHash}, token_version = token_version + 1 "
            + "WHERE id = #{id}")
    int updatePasswordAndBumpVersion(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Select("SELECT COUNT(*) FROM t_user")
    long count();
}
