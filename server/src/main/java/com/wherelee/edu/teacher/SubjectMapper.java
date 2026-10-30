package com.wherelee.edu.teacher;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SubjectMapper {

    String COLUMNS = "id, code, name, sort, created_at";

    @Select("SELECT " + COLUMNS + " FROM t_subject ORDER BY sort, id")
    List<Subject> findAll();

    @Select("SELECT " + COLUMNS + " FROM t_subject WHERE id = #{id}")
    Subject findById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM t_subject WHERE code = #{code}")
    Subject findByCode(@Param("code") String code);

    @Select("SELECT " + COLUMNS + " FROM t_subject WHERE id IN "
            + "(SELECT subject_id FROM t_teacher_subject WHERE teacher_id = #{teacherId}) ORDER BY sort, id")
    List<Subject> findByTeacherId(@Param("teacherId") Long teacherId);

    @Insert("INSERT INTO t_subject (code, name, sort) VALUES (#{code}, #{name}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Subject subject);

    @Delete("DELETE FROM t_subject WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 删除前检查是否还有讲师挂在这个学科下 */
    @Select("SELECT COUNT(*) FROM t_teacher_subject WHERE subject_id = #{subjectId}")
    long countTeachers(@Param("subjectId") Long subjectId);
}
