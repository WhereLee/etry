package com.wherelee.edu.course;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseReviewMapper {

    String COLUMNS = "id, course_id, user_id, rating, content, created_at, updated_at";

    /**
     * 提交评价：有则更新，无则插入。
     * 唯一键 (user_id, course_id) 保证一人一课只有一条，重复提交是「改评价」而不是刷分。
     */
    @Insert("INSERT INTO t_course_review (course_id, user_id, rating, content) "
            + "VALUES (#{courseId}, #{userId}, #{rating}, #{content}) "
            + "ON DUPLICATE KEY UPDATE rating = VALUES(rating), content = VALUES(content)")
    int upsert(@Param("courseId") Long courseId,
               @Param("userId") Long userId,
               @Param("rating") int rating,
               @Param("content") String content);

    @Select("SELECT " + COLUMNS + " FROM t_course_review WHERE user_id = #{userId} AND course_id = #{courseId}")
    CourseReview findByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("SELECT r.id, r.user_id, u.nickname, r.rating, r.content, r.created_at "
            + "FROM t_course_review r JOIN t_user u ON u.id = r.user_id "
            + "WHERE r.course_id = #{courseId} "
            + "ORDER BY r.created_at DESC, r.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<ReviewRow> findPageByCourse(@Param("courseId") Long courseId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_course_review WHERE course_id = #{courseId}")
    long countByCourse(@Param("courseId") Long courseId);

    /** 课程详情里显示「几点几分、几条评价」 */
    @Select("SELECT IFNULL(AVG(rating), 0) AS avg_rating, COUNT(*) AS total "
            + "FROM t_course_review WHERE course_id = #{courseId}")
    RatingStat statByCourse(@Param("courseId") Long courseId);

    @Delete("DELETE FROM t_course_review WHERE user_id = #{userId} AND course_id = #{courseId}")
    int deleteByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Delete("DELETE FROM t_course_review WHERE course_id = #{courseId}")
    int deleteByCourse(@Param("courseId") Long courseId);
}
