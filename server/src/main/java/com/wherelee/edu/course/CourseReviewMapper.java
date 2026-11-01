package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CourseReviewMapper {

    String COLUMNS = "id, course_id, user_id, rating, content, hidden, created_at, updated_at";

    /**
     * 提交评价：有则更新，无则插入。
     * 唯一键 (user_id, course_id) 保证一人一课只有一条，重复提交是「改评价」而不是刷分。
     * 更新时重置 hidden：内容变了，之前的隐藏理由重新审（管理员不妥可再隐藏）。
     */
    @Insert("INSERT INTO t_course_review (course_id, user_id, rating, content) "
            + "VALUES (#{courseId}, #{userId}, #{rating}, #{content}) "
            + "ON DUPLICATE KEY UPDATE rating = VALUES(rating), content = VALUES(content), hidden = 0")
    int upsert(@Param("courseId") Long courseId,
               @Param("userId") Long userId,
               @Param("rating") int rating,
               @Param("content") String content);

    @Select("SELECT " + COLUMNS + " FROM t_course_review WHERE user_id = #{userId} AND course_id = #{courseId}")
    CourseReview findByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("SELECT " + COLUMNS + " FROM t_course_review WHERE id = #{id}")
    CourseReview findById(@Param("id") Long id);

    /** 前台评价列表：被管理员隐藏的不出现 */
    @Select("SELECT r.id, r.user_id, u.nickname, r.rating, r.content, r.created_at "
            + "FROM t_course_review r JOIN t_user u ON u.id = r.user_id "
            + "WHERE r.course_id = #{courseId} AND r.hidden = 0 "
            + "ORDER BY r.created_at DESC, r.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<ReviewRow> findPageByCourse(@Param("courseId") Long courseId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_course_review WHERE course_id = #{courseId} AND hidden = 0")
    long countByCourse(@Param("courseId") Long courseId);

    /** 课程详情里显示「几点几分、几条评价」，隐藏的不计入 */
    @Select("SELECT IFNULL(AVG(rating), 0) AS avg_rating, COUNT(*) AS total "
            + "FROM t_course_review WHERE course_id = #{courseId} AND hidden = 0")
    RatingStat statByCourse(@Param("courseId") Long courseId);

    /** 软删：管理员隐藏。不物理删，审计可对账；已隐藏时再次执行是幂等 */
    @Update("UPDATE t_course_review SET hidden = 1 WHERE id = #{id}")
    int markHidden(@Param("id") Long id);

    @Delete("DELETE FROM t_course_review WHERE user_id = #{userId} AND course_id = #{courseId}")
    int deleteByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Delete("DELETE FROM t_course_review WHERE course_id = #{courseId}")
    int deleteByCourse(@Param("courseId") Long courseId);
}
