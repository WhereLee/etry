package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseMaterialMapper {

    String COLUMNS = "id, course_id, title, file_name, file_key, size_bytes, sort, created_at";

    @Insert("INSERT INTO t_course_material (course_id, title, file_name, file_key, size_bytes, sort) "
            + "VALUES (#{courseId}, #{title}, #{fileName}, #{fileKey}, #{sizeBytes}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseMaterial material);

    @Select("SELECT " + COLUMNS + " FROM t_course_material WHERE id = #{id}")
    CourseMaterial findById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM t_course_material WHERE course_id = #{courseId} "
            + "ORDER BY sort, id")
    List<CourseMaterial> findByCourseId(@Param("courseId") Long courseId);

    @Delete("DELETE FROM t_course_material WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM t_course_material WHERE course_id = #{courseId}")
    int deleteByCourse(@Param("courseId") Long courseId);
}
