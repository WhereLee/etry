package com.wherelee.edu.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 跨表聚合统计。
 *
 * 没有单独的统计表：数据量在这个规模下，几条 COUNT 直接查主表最快，
 * 也不会出现「报表和真实数据对不上」的问题。等哪天慢到影响体验，
 * 再考虑定时汇总到一张日结表里。
 */
@Mapper
public interface StatsMapper {

    @Select("SELECT COUNT(*) FROM t_course")
    long countCourses();

    @Select("SELECT COUNT(*) FROM t_course WHERE status = #{status}")
    long countCoursesByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM t_teacher_profile WHERE status = #{status}")
    long countTeachersByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM t_user")
    long countUsers();

    @Select("SELECT COUNT(*) FROM t_user WHERE status = 'ACTIVE'")
    long countActiveUsers();

    /** 学习记录条数（一个人学一节课算一条） */
    @Select("SELECT COUNT(*) FROM t_learning_progress")
    long countLearningRecords();

    @Select("SELECT COUNT(*) FROM t_learning_progress WHERE completed = 1")
    long countCompletedLessons();

    /**
     * 热门课程：按「有多少不同的人学过」排序，不是按播放次数——
     * 一个人反复刷同一节课不代表它热门。
     */
    @Select("SELECT c.id AS course_id, c.title, COUNT(DISTINCT p.user_id) AS learners "
            + "FROM t_learning_progress p "
            + "JOIN t_course c ON c.id = p.course_id "
            + "GROUP BY c.id, c.title "
            + "ORDER BY learners DESC, c.id DESC "
            + "LIMIT #{limit}")
    List<TopCourseRow> topCourses(@Param("limit") int limit);
}
