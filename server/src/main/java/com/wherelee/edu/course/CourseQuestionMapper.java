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
public interface CourseQuestionMapper {

    String COLUMNS = "id, course_id, user_id, content, resolved, created_at";

    @Insert("INSERT INTO t_course_question (course_id, user_id, content, resolved) "
            + "VALUES (#{courseId}, #{userId}, #{content}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseQuestion question);

    @Select("SELECT " + COLUMNS + " FROM t_course_question WHERE id = #{id}")
    CourseQuestion findById(@Param("id") Long id);

    /**
     * 问题列表。未解决的排前面——学员进来看的是「有没有人问过类似问题」。
     *
     * 注意列顺序必须和 QuestionRow 的字段顺序一致：
     * MyBatis 给 record 赋值是按列位置来的，顺序错了会报
     * 「Unsupported conversion from DATETIME to java.lang.Long」。
     */
    @Select("SELECT q.id, q.user_id, u.nickname, q.content, q.resolved, "
            + "(SELECT COUNT(*) FROM t_course_answer a WHERE a.question_id = q.id) AS answer_count, "
            + "q.created_at "
            + "FROM t_course_question q JOIN t_user u ON u.id = q.user_id "
            + "WHERE q.course_id = #{courseId} "
            + "ORDER BY q.resolved ASC, q.created_at DESC, q.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<QuestionRow> findPageByCourse(@Param("courseId") Long courseId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_course_question WHERE course_id = #{courseId}")
    long countByCourse(@Param("courseId") Long courseId);

    @Update("UPDATE t_course_question SET resolved = #{resolved} WHERE id = #{id}")
    int updateResolved(@Param("id") Long id, @Param("resolved") boolean resolved);

    @Delete("DELETE FROM t_course_question WHERE course_id = #{courseId}")
    int deleteByCourse(@Param("courseId") Long courseId);

    /**
     * 讲师工作台用：某个讲师名下所有课程里，「有人问但还没有任何回答」的问题数。
     * 这是讲师最该先处理的事。
     */
    @Select("SELECT COUNT(*) FROM t_course_question q "
            + "JOIN t_course c ON c.id = q.course_id "
            + "WHERE c.teacher_id = #{teacherId} "
            + "AND NOT EXISTS (SELECT 1 FROM t_course_answer a WHERE a.question_id = q.id)")
    long countUnansweredByTeacher(@Param("teacherId") Long teacherId);
}
