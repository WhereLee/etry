package com.wherelee.edu.teacher;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeacherSubjectMapper {

    @Select("SELECT subject_id FROM t_teacher_subject WHERE teacher_id = #{teacherId}")
    List<Long> findSubjectIds(@Param("teacherId") Long teacherId);

    @Delete("DELETE FROM t_teacher_subject WHERE teacher_id = #{teacherId}")
    int deleteByTeacherId(@Param("teacherId") Long teacherId);

    int batchInsert(@Param("teacherId") Long teacherId, @Param("subjectIds") List<Long> subjectIds);
}
