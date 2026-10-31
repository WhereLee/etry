package com.wherelee.edu.course.enrollment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CourseEnrollmentMapper {

    String COLUMNS = "id, user_id, course_id, status, lesson_count_snapshot, "
            + "joined_at, last_active_at, created_at, updated_at";

    /**
     * 加入课程。已存在就什么都不做——重复点不该报错，也不该产生第二条。
     * 课时数快照在这里一起写进去，用来感知讲师之后的增删。
     */
    @Insert("INSERT IGNORE INTO t_course_enrollment "
            + "(user_id, course_id, status, lesson_count_snapshot, joined_at, last_active_at) "
            + "VALUES (#{userId}, #{courseId}, #{status}, #{lessonCount}, NOW(3), NOW(3))")
    int insertIgnore(@Param("userId") Long userId,
                     @Param("courseId") Long courseId,
                     @Param("status") String status,
                     @Param("lessonCount") int lessonCount);

    @Select("SELECT " + COLUMNS + " FROM t_course_enrollment "
            + "WHERE user_id = #{userId} AND course_id = #{courseId}")
    CourseEnrollment findByUserAndCourse(@Param("userId") Long userId,
                                         @Param("courseId") Long courseId);

    /**
     * 更新关系状态。active 为 true 时顺带刷新最近活跃时间——
     * "加入"和"开始学"都要刷新，但只有后者算一次学习行为。
     */
    @Update("UPDATE t_course_enrollment SET status = #{status}, "
            + "lesson_count_snapshot = #{lessonCount}, last_active_at = NOW(3) "
            + "WHERE user_id = #{userId} AND course_id = #{courseId}")
    int updateStatus(@Param("userId") Long userId,
                     @Param("courseId") Long courseId,
                     @Param("status") String status,
                     @Param("lessonCount") int lessonCount);

    /**
     * 我的学习：以关系表为主干，课程状态和进度各自聚合。
     *
     * QUIT 的不出现——移出就是移出，但记录还在，重新学的时候进度不会丢。
     */
    @Select("SELECT c.id AS course_id, c.title, c.cover_key, c.status AS course_status, "
            + "e.status AS relation_status, e.lesson_count_snapshot, e.last_active_at, "
            + "(SELECT COUNT(*) FROM t_course_lesson l WHERE l.course_id = c.id "
            + "  AND l.video_key IS NOT NULL AND l.video_key != '') AS total_lessons, "
            + "(SELECT COUNT(*) FROM t_learning_progress p WHERE p.user_id = e.user_id "
            + "  AND p.course_id = e.course_id AND p.completed = 1) AS completed_lessons "
            + "FROM t_course_enrollment e "
            + "JOIN t_course c ON c.id = e.course_id "
            + "WHERE e.user_id = #{userId} AND e.status != 'QUIT' "
            + "ORDER BY e.last_active_at DESC, e.id DESC "
            + "LIMIT #{limit}")
    List<EnrollmentRow> findActiveByUser(@Param("userId") Long userId,
                                         @Param("limit") int limit);

    /** 最近一门在学的课，首页"继续学习"用 */
    @Select("SELECT " + COLUMNS + " FROM t_course_enrollment "
            + "WHERE user_id = #{userId} AND status IN ('LEARNING', 'JOINED') "
            + "ORDER BY last_active_at DESC LIMIT 1")
    CourseEnrollment findLatestActive(@Param("userId") Long userId);

    /**
     * 播放时刷新活跃时间，并把状态推进到"在学"。
     *
     * 一条 UPDATE 同时做两件事，避免每 10 秒上报时先查再写：
     * 已学完的不退回（CASE 里保住 COMPLETED），移出过的重新学回到 LEARNING。
     * 不在这里写课时数快照——那需要一次 COUNT，而这条路每 10 秒就走一次。
     * 返回 0 表示还没有关系记录，由调用方补一条。
     */
    @Update("UPDATE t_course_enrollment SET "
            + "status = CASE WHEN status = 'COMPLETED' THEN 'COMPLETED' ELSE 'LEARNING' END, "
            + "last_active_at = NOW(3) "
            + "WHERE user_id = #{userId} AND course_id = #{courseId} AND status != 'QUIT'")
    int touchLearning(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * 全部课时学完。带上 QUIT 排除：已经移出的人不该因为另一个标签页里的
     * 残留心跳被改成「已学完」.
     */
    @Update("UPDATE t_course_enrollment SET status = 'COMPLETED', "
            + "lesson_count_snapshot = #{lessonCount}, last_active_at = NOW(3) "
            + "WHERE user_id = #{userId} AND course_id = #{courseId} AND status != 'QUIT'")
    int markCompleted(@Param("userId") Long userId,
                      @Param("courseId") Long courseId,
                      @Param("lessonCount") int lessonCount);

    /** 这门课有多少节可学的（有视频的） */
    @Select("SELECT COUNT(*) FROM t_course_lesson "
            + "WHERE course_id = #{courseId} AND video_key IS NOT NULL AND video_key != ''")
    int countLearnableLessons(@Param("courseId") Long courseId);

    /** 这个人在这门课里已经学完几节 */
    @Select("SELECT COUNT(*) FROM t_learning_progress "
            + "WHERE user_id = #{userId} AND course_id = #{courseId} AND completed = 1")
    int countCompletedLessons(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
