package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseAnswerMapper {

    @Insert("INSERT INTO t_course_answer (question_id, user_id, content) "
            + "VALUES (#{questionId}, #{userId}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseAnswer answer);

    @Select("SELECT a.id, a.user_id, u.nickname, a.content, a.created_at "
            + "FROM t_course_answer a JOIN t_user u ON u.id = a.user_id "
            + "WHERE a.question_id = #{questionId} "
            + "ORDER BY a.created_at, a.id")
    List<AnswerRow> findByQuestionId(@Param("questionId") Long questionId);

    @Select("SELECT COUNT(*) FROM t_course_answer WHERE question_id = #{questionId}")
    long countByQuestion(@Param("questionId") Long questionId);

    @Delete("DELETE FROM t_course_answer WHERE question_id = #{questionId}")
    int deleteByQuestion(@Param("questionId") Long questionId);
}
