package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CourseChapterMapper {

    String COLUMNS = "id, course_id, title, sort, created_at";

    @Select("SELECT " + COLUMNS + " FROM t_course_chapter WHERE course_id = #{courseId} "
            + "ORDER BY sort, id")
    List<CourseChapter> findByCourseId(@Param("courseId") Long courseId);

    @Select("SELECT " + COLUMNS + " FROM t_course_chapter WHERE id = #{id}")
    CourseChapter findById(@Param("id") Long id);

    @Insert("INSERT INTO t_course_chapter (course_id, title, sort) VALUES (#{courseId}, #{title}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseChapter chapter);

    @Update("UPDATE t_course_chapter SET title = #{title}, sort = #{sort} WHERE id = #{id}")
    int update(CourseChapter chapter);

    @Delete("DELETE FROM t_course_chapter WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM t_course_chapter WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
}
