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
public interface TagMapper {

    String COLUMNS = "id, name, sort, created_at";

    @Select("SELECT " + COLUMNS + " FROM t_tag ORDER BY sort, id")
    List<Tag> findAll();

    @Select("SELECT " + COLUMNS + " FROM t_tag WHERE id = #{id}")
    Tag findById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM t_tag WHERE name = #{name}")
    Tag findByName(@Param("name") String name);

    @Insert("INSERT INTO t_tag (name, sort) VALUES (#{name}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    @Update("UPDATE t_tag SET name = #{name}, sort = #{sort} WHERE id = #{id}")
    int update(Tag tag);

    @Delete("DELETE FROM t_tag WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 删除前检查：还有课程挂着这个标签就不让删 */
    @Select("SELECT COUNT(*) FROM t_course_tag WHERE tag_id = #{tagId}")
    long countCourses(@Param("tagId") Long tagId);
}
