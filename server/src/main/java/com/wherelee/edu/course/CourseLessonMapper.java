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
public interface CourseLessonMapper {

    String COLUMNS = "id, course_id, chapter_id, title, video_key, duration_seconds, size_bytes, "
            + "sort, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM t_course_lesson WHERE course_id = #{courseId} "
            + "ORDER BY chapter_id, sort, id")
    List<CourseLesson> findByCourseId(@Param("courseId") Long courseId);

    /**
     * 按「章节 sort、课时 sort」的全课顺序取课时，顺序学习的前置校验用。
     *
     * 只算有视频的课时：没视频的课时上报不了进度、永远完不成，
     * 一旦计入顺序，它后面的课时会被永久锁死。
     */
    @Select("SELECT l.* FROM t_course_lesson l JOIN t_course_chapter c ON c.id = l.chapter_id "
            + "WHERE l.course_id = #{courseId} AND l.video_key IS NOT NULL AND l.video_key != '' "
            + "ORDER BY c.sort, c.id, l.sort, l.id")
    List<CourseLesson> findOrderedByCourseId(@Param("courseId") Long courseId);

    @Select("SELECT " + COLUMNS + " FROM t_course_lesson WHERE chapter_id = #{chapterId} ORDER BY sort, id")
    List<CourseLesson> findByChapterId(@Param("chapterId") Long chapterId);

    @Select("SELECT " + COLUMNS + " FROM t_course_lesson WHERE id = #{id}")
    CourseLesson findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM t_course_lesson WHERE course_id = #{courseId}")
    long countByCourse(@Param("courseId") Long courseId);

    /**
     * 列表页用：一次查出多门课的课时数，避免每行一次 count。
     * 同时给出「能学的课时数」：列表显示全量，学习页的分母只算有视频的，
     * 两个数都给前端，避免「列表说 5 节、进去说共 3 节」。
     */
    @Select("<script>SELECT course_id, COUNT(*) AS cnt, "
            + "SUM(CASE WHEN video_key IS NOT NULL AND video_key != '' THEN 1 ELSE 0 END) AS video_cnt "
            + "FROM t_course_lesson WHERE course_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY course_id</script>")
    List<CourseLessonCount> countByCourseIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO t_course_lesson (course_id, chapter_id, title, video_key, duration_seconds, "
            + "size_bytes, sort) VALUES (#{courseId}, #{chapterId}, #{title}, #{videoKey}, "
            + "#{durationSeconds}, #{sizeBytes}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseLesson lesson);

    @Update("UPDATE t_course_lesson SET title = #{title}, sort = #{sort} WHERE id = #{id}")
    int update(CourseLesson lesson);

    /** 视频信息由前端上传完回填，单独一个方法避免和标题修改互相覆盖 */
    @Update("UPDATE t_course_lesson SET video_key = #{videoKey}, duration_seconds = #{durationSeconds}, "
            + "size_bytes = #{sizeBytes} WHERE id = #{id}")
    int updateVideo(@Param("id") Long id,
                    @Param("videoKey") String videoKey,
                    @Param("durationSeconds") Integer durationSeconds,
                    @Param("sizeBytes") Long sizeBytes);

    @Delete("DELETE FROM t_course_lesson WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM t_course_lesson WHERE chapter_id = #{chapterId}")
    int deleteByChapterId(@Param("chapterId") Long chapterId);

    @Delete("DELETE FROM t_course_lesson WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
}
