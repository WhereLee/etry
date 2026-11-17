package com.wherelee.edu.modules.course.content;

import com.wherelee.edu.modules.learning.api.CourseContentQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 课程内容查询适配器：course 暴露视频元数据（videoId → seriesId/duration）给 learning，
 * 只读 course 自己归属的内容表。
 */
@Component
public class CourseContentQueryAdapter implements CourseContentQuery {

    private final JdbcTemplate jdbc;

    public CourseContentQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VideoRef> findVideo(Long videoId) {
        return jdbc.query("""
                SELECT v.id AS video_id, c.series_id, v.duration_seconds
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                WHERE v.id = ?
                """, (rs, rowNum) -> new VideoRef(
                rs.getLong("video_id"), rs.getLong("series_id"), rs.getInt("duration_seconds")), videoId)
                .stream().findFirst();
    }
}