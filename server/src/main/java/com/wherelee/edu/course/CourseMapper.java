package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CourseMapper {

    String COLUMNS = "id, teacher_id, subject_id, title, summary, description, cover_key, status, "
            + "reject_reason, published_at, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_course WHERE id = #{id}")
    Course findById(@Param("id") Long id);

    @Insert("INSERT INTO t_course (teacher_id, subject_id, title, summary, description, cover_key, status) "
            + "VALUES (#{teacherId}, #{subjectId}, #{title}, #{summary}, #{description}, #{coverKey}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Course course);

    /** 改内容时顺带把状态写回：被驳回的课改完后要重新提交，状态由业务层决定 */
    @Update("UPDATE t_course SET subject_id = #{subjectId}, title = #{title}, summary = #{summary}, "
            + "description = #{description}, cover_key = #{coverKey}, status = #{status}, "
            + "reject_reason = #{rejectReason} WHERE id = #{id}")
    int update(Course course);

    @Update("UPDATE t_course SET status = #{status}, reject_reason = #{rejectReason}, "
            + "published_at = #{publishedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason,
                     @Param("publishedAt") LocalDateTime publishedAt);

    @Delete("DELETE FROM t_course WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 老师自己的课程列表，走 idx_course_teacher(teacher_id, status) */
    List<Course> findPageByTeacher(@Param("teacherId") Long teacherId,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countByTeacher(@Param("teacherId") Long teacherId, @Param("status") String status);

    /** 前台只看已上架，走 idx_course_status(status, published_at) */
    List<Course> findPublicPage(@Param("subjectId") Long subjectId,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    long countPublic(@Param("subjectId") Long subjectId, @Param("keyword") String keyword);

    /** 后台审核列表：状态可选，默认看全部 */
    List<Course> findAdminPage(@Param("status") String status,
                               @Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countAdmin(@Param("status") String status, @Param("keyword") String keyword);
}
