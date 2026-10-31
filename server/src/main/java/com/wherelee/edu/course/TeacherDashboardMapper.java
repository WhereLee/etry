package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 讲师工作台的聚合查询。
 *
 * 「完课」的定义：该学员在这门课里完成的课时数 = 课程总课时数。
 * 用 HAVING SUM(completed) >= 总课时 表达，比在 Java 里二次聚合更省事。
 *
 * 工作台不是高频接口，几个相关子查询可以接受；真慢了再拆汇总表。
 */
@Mapper
public interface TeacherDashboardMapper {

    /** 学过我任何一门课的人数，跨课程去重 */
    @Select("SELECT COUNT(DISTINCT p.user_id) "
            + "FROM t_learning_progress p JOIN t_course c ON c.id = p.course_id "
            + "WHERE c.teacher_id = #{teacherId}")
    long countLearners(@Param("teacherId") Long teacherId);

    /** 完课人数：至少把某一门课的课时全部学完 */
    @Select("SELECT COUNT(*) FROM ("
            + "  SELECT p.user_id, p.course_id "
            + "  FROM t_learning_progress p JOIN t_course c ON c.id = p.course_id "
            + "  WHERE c.teacher_id = #{teacherId} "
            + "  GROUP BY p.user_id, p.course_id "
            + "  HAVING SUM(p.completed) >= "
            + "    (SELECT COUNT(*) FROM t_course_lesson l WHERE l.course_id = p.course_id "
            + "      AND l.video_key IS NOT NULL AND l.video_key <> '')"
            + ") t")
    long countCompleters(@Param("teacherId") Long teacherId);

    @Select("SELECT COUNT(*) FROM t_course WHERE teacher_id = #{teacherId}")
    long countCourses(@Param("teacherId") Long teacherId);

    @Select("SELECT COUNT(*) FROM t_course WHERE teacher_id = #{teacherId} AND status = 'PUBLISHED'")
    long countPublishedCourses(@Param("teacherId") Long teacherId);

    /** 我所有课程的评价均分与总条数 */
    @Select("SELECT IFNULL(AVG(r.rating), 0) AS avg_rating, COUNT(*) AS total "
            + "FROM t_course_review r JOIN t_course c ON c.id = r.course_id "
            + "WHERE c.teacher_id = #{teacherId}")
    RatingStat ratingStat(@Param("teacherId") Long teacherId);

    /** 每门课的明细 */
    @Select("SELECT c.id AS course_id, c.title, c.status, "
            + "  (SELECT COUNT(DISTINCT p.user_id) FROM t_learning_progress p "
            + "     WHERE p.course_id = c.id) AS learners, "
            + "  (SELECT COUNT(*) FROM ("
            + "      SELECT p2.user_id FROM t_learning_progress p2 WHERE p2.course_id = c.id "
            + "      GROUP BY p2.user_id "
            + "      HAVING SUM(p2.completed) >= "
            + "        (SELECT COUNT(*) FROM t_course_lesson l WHERE l.course_id = c.id "
            + "          AND l.video_key IS NOT NULL AND l.video_key <> '')"
            + "  ) x) AS completers, "
            + "  (SELECT IFNULL(AVG(r.rating), 0) FROM t_course_review r "
            + "     WHERE r.course_id = c.id) AS avg_rating, "
            + "  (SELECT COUNT(*) FROM t_course_review r WHERE r.course_id = c.id) AS rating_count, "
            + "  (SELECT COUNT(*) FROM t_course_question q WHERE q.course_id = c.id "
            + "     AND NOT EXISTS (SELECT 1 FROM t_course_answer a WHERE a.question_id = q.id)) AS unanswered "
            + "FROM t_course c "
            + "WHERE c.teacher_id = #{teacherId} "
            + "ORDER BY c.id DESC")
    List<CourseStatRow> courseStats(@Param("teacherId") Long teacherId);
}
