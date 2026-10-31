package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseFavoriteMapper {

    /**
     * 收藏。INSERT IGNORE 利用唯一键做幂等：
     * 已经收藏过再点一次不会报错，也不会产生第二条。
     */
    @Insert("INSERT IGNORE INTO t_course_favorite (user_id, course_id) "
            + "VALUES (#{userId}, #{courseId})")
    int insertIgnore(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Delete("DELETE FROM t_course_favorite WHERE user_id = #{userId} AND course_id = #{courseId}")
    int delete(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("SELECT COUNT(*) FROM t_course_favorite WHERE user_id = #{userId} AND course_id = #{courseId}")
    long count(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("SELECT COUNT(*) FROM t_course_favorite WHERE course_id = #{courseId}")
    long countByCourse(@Param("courseId") Long courseId);

    /**
     * 我的收藏：直接 join 出课程实体，交给 CourseAssembler 统一组装，
     * 不另造一套 VO，避免列表字段和别处不一致。
     */
    @Select("SELECT c.id, c.teacher_id, c.subject_id, c.title, c.summary, c.description, "
            + "c.cover_key, c.status, c.reject_reason, c.published_at, c.created_at, c.updated_at "
            + "FROM t_course_favorite f JOIN t_course c ON c.id = f.course_id "
            + "WHERE f.user_id = #{userId} "
            + "ORDER BY f.created_at DESC, f.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Course> findCoursesByUser(@Param("userId") Long userId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_course_favorite WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);

    /** 课程被删时清理收藏，避免留下指向不存在课程的记录 */
    @Delete("DELETE FROM t_course_favorite WHERE course_id = #{courseId}")
    int deleteByCourse(@Param("courseId") Long courseId);
}
