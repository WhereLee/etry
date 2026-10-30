package com.wherelee.edu.teacher;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TeacherProfileMapper {

    String COLUMNS = "id, user_id, real_name, title, bio, avatar_key, years, status, reject_reason, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_teacher_profile WHERE user_id = #{userId}")
    TeacherProfile findByUserId(@Param("userId") Long userId);

    @Select("SELECT " + COLUMNS + " FROM t_teacher_profile WHERE id = #{id}")
    TeacherProfile findById(@Param("id") Long id);

    @Insert("INSERT INTO t_teacher_profile (user_id, real_name, title, bio, avatar_key, years, status) "
            + "VALUES (#{userId}, #{realName}, #{title}, #{bio}, #{avatarKey}, #{years}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeacherProfile profile);

    @Update("UPDATE t_teacher_profile SET real_name = #{realName}, title = #{title}, bio = #{bio}, "
            + "years = #{years}, status = #{status}, reject_reason = NULL WHERE id = #{id}")
    int updateProfile(TeacherProfile profile);

    @Update("UPDATE t_teacher_profile SET status = #{status}, reject_reason = #{rejectReason} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("rejectReason") String rejectReason);

    /** 审核列表：按状态筛 + 时间倒序，走 idx_teacher_status */
    List<TeacherProfile> findPage(@Param("status") String status,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    long countPage(@Param("status") String status);
}
