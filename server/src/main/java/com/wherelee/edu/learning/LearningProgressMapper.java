package com.wherelee.edu.learning;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LearningProgressMapper {

    String COLUMNS = "id, user_id, course_id, lesson_id, position_seconds, duration_seconds, "
            + "completed, created_at, updated_at";

    /**
     * 上报进度：有则更新，无则插入，一条 SQL 完成。
     *
     * 不做「先查再写」——播放器每 10 秒上报一次，两个请求挨在一起时，
     * 先查后写会让后到的旧位置覆盖新位置。
     *
     * completed 用 GREATEST 而不是直接赋值：看到过结尾就算学过，
     * 回看开头时不能把它清回 false。
     */
    @Insert("INSERT INTO t_learning_progress "
            + "(user_id, course_id, lesson_id, position_seconds, duration_seconds, completed) "
            + "VALUES (#{userId}, #{courseId}, #{lessonId}, #{positionSeconds}, #{durationSeconds}, #{completed}) "
            + "ON DUPLICATE KEY UPDATE "
            + "course_id = VALUES(course_id), "
            + "position_seconds = VALUES(position_seconds), "
            + "duration_seconds = VALUES(duration_seconds), "
            + "completed = GREATEST(completed, VALUES(completed))")
    int upsert(@Param("userId") Long userId,
               @Param("courseId") Long courseId,
               @Param("lessonId") Long lessonId,
               @Param("positionSeconds") int positionSeconds,
               @Param("durationSeconds") int durationSeconds,
               @Param("completed") boolean completed);

    @Select("SELECT " + COLUMNS + " FROM t_learning_progress "
            + "WHERE user_id = #{userId} AND course_id = #{courseId}")
    List<LearningProgress> findByUserAndCourse(@Param("userId") Long userId,
                                               @Param("courseId") Long courseId);

    @Select("SELECT " + COLUMNS + " FROM t_learning_progress "
            + "WHERE user_id = #{userId} AND lesson_id = #{lessonId}")
    LearningProgress findByUserAndLesson(@Param("userId") Long userId,
                                         @Param("lessonId") Long lessonId);

    /** 学过没有：评价一门课的前提是有学习记录 */
    @Select("SELECT COUNT(*) FROM t_learning_progress WHERE user_id = #{userId} AND course_id = #{courseId}")
    long countByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * 我的学习：按最近学习时间倒序列出课程，带进度。
     * 走 idx_progress_user_course(user_id, course_id, updated_at)。
     */
    @Select("SELECT c.id AS course_id, c.title, c.cover_key, "
            + "(SELECT COUNT(*) FROM t_course_lesson l WHERE l.course_id = c.id) AS total_lessons, "
            + "COUNT(CASE WHEN p.completed = 1 THEN 1 END) AS completed_lessons, "
            + "MAX(p.updated_at) AS last_learned_at "
            + "FROM t_learning_progress p "
            + "JOIN t_course c ON c.id = p.course_id "
            + "WHERE p.user_id = #{userId} "
            + "GROUP BY c.id, c.title, c.cover_key "
            + "ORDER BY MAX(p.updated_at) DESC "
            + "LIMIT #{limit}")
    List<MyLearningRow> findRecentCourses(@Param("userId") Long userId, @Param("limit") int limit);
}
