package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseTagMapper {

    @Insert("<script>INSERT INTO t_course_tag (course_id, tag_id) VALUES "
            + "<foreach collection='tagIds' item='tagId' separator=','>(#{courseId}, #{tagId})</foreach>"
            + "</script>")
    int batchInsert(@Param("courseId") Long courseId, @Param("tagIds") List<Long> tagIds);

    @Delete("DELETE FROM t_course_tag WHERE course_id = #{courseId}")
    int deleteByCourse(@Param("courseId") Long courseId);

    @Select("SELECT tag_id FROM t_course_tag WHERE course_id = #{courseId}")
    List<Long> findTagIdsByCourse(@Param("courseId") Long courseId);

    /** 详情页展示标签：直接 join 出标签实体，一次查完 */
    @Select("SELECT t.id, t.name, t.sort, t.created_at "
            + "FROM t_course_tag ct JOIN t_tag t ON t.id = ct.tag_id "
            + "WHERE ct.course_id = #{courseId} "
            + "ORDER BY t.sort, t.id")
    List<Tag> findTagsByCourse(@Param("courseId") Long courseId);
}
