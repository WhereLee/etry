package com.wherelee.edu.teacher;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface TeacherProfileMapper {

    String COLUMNS = "id, user_id, real_name, title, bio, avatar_key, years, status, reject_reason, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_teacher_profile WHERE user_id = #{userId}")
    TeacherProfile findByUserId(@Param("userId") Long userId);

    @Select("SELECT " + COLUMNS + " FROM t_teacher_profile WHERE id = #{id}")
    TeacherProfile findById(@Param("id") Long id);

    /** 列表页用：一次查出多份档案的姓名，避免每行一次查询 */
    @Select("<script>SELECT id, user_id, real_name FROM t_teacher_profile WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    @MapKey("id")
    Map<Long, TeacherProfile> findByIds(@Param("ids") List<Long> ids);

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

    @Update("UPDATE t_teacher_profile SET avatar_key = #{avatarKey} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarKey") String avatarKey);

    /** 审核列表：按状态筛 + 时间倒序，走 idx_teacher_status */
    List<TeacherProfile> findPage(@Param("status") String status,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    long countPage(@Param("status") String status);
}
