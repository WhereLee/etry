package com.wherelee.edu.series;

import com.wherelee.edu.series.dto.SeriesDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SeriesRepository {

    private final JdbcTemplate jdbc;

    public SeriesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SeriesDtos.SeriesSummary> findForStudent(Long userId) {
        return jdbc.query("""
                SELECT s.id,
                       s.title,
                       s.summary,
                       u.nickname AS teacher_name,
                       COUNT(DISTINCT c.id) AS chapter_count,
                       COUNT(DISTINCT v.id) AS video_count,
                       COUNT(DISTINCT CASE WHEN p.completed = 1 THEN v.id END) AS completed_video_count,
                       MIN(ss.sort) AS student_sort
                FROM t_student_series ss
                JOIN t_series s ON s.id = ss.series_id
                JOIN t_user u ON u.id = s.teacher_id
                LEFT JOIN t_series_chapter c ON c.series_id = s.id
                LEFT JOIN t_series_video v ON v.chapter_id = c.id
                LEFT JOIN t_learning_progress p ON p.user_id = ss.user_id AND p.video_id = v.id
                WHERE ss.user_id = ? AND ss.status = 'ACTIVE'
                  AND (ss.valid_to IS NULL OR ss.valid_to > CURRENT_TIMESTAMP(3))
                GROUP BY s.id, s.title, s.summary, u.nickname
                ORDER BY student_sort, s.id
                """, (rs, rowNum) -> new SeriesDtos.SeriesSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("teacher_name"),
                rs.getLong("chapter_count"),
                rs.getLong("video_count"),
                rs.getLong("completed_video_count")
        ), userId);
    }

    public Optional<SeriesHeader> findAssignedSeries(Long userId, Long seriesId) {
        List<SeriesHeader> rows = jdbc.query("""
                SELECT s.id, s.title, s.summary, u.nickname AS teacher_name
                FROM t_series s
                JOIN t_student_series ss ON ss.series_id = s.id AND ss.user_id = ?
                JOIN t_user u ON u.id = s.teacher_id
                WHERE s.id = ? AND ss.status = 'ACTIVE'
                  AND (ss.valid_to IS NULL OR ss.valid_to > CURRENT_TIMESTAMP(3))
                """, (rs, rowNum) -> new SeriesHeader(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("teacher_name")
        ), userId, seriesId);
        return rows.stream().findFirst();
    }

    public List<ChapterRow> findChapters(Long seriesId) {
        return jdbc.query("""
                SELECT id, title
                FROM t_series_chapter
                WHERE series_id = ?
                ORDER BY sort, id
                """, (rs, rowNum) -> new ChapterRow(rs.getLong("id"), rs.getString("title")), seriesId);
    }

    public List<VideoRow> findVideos(Long seriesId, Long userId) {
        return jdbc.query("""
                SELECT v.id, v.chapter_id, v.title, v.duration_seconds,
                       COALESCE(p.position_seconds, 0) AS position_seconds,
                       COALESCE(p.completed, 0) AS completed
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                LEFT JOIN t_learning_progress p ON p.user_id = ? AND p.video_id = v.id
                WHERE c.series_id = ?
                ORDER BY c.sort, c.id, v.sort, v.id
                """, (rs, rowNum) -> new VideoRow(
                rs.getLong("id"),
                rs.getLong("chapter_id"),
                rs.getString("title"),
                rs.getInt("duration_seconds"),
                rs.getInt("position_seconds"),
                rs.getBoolean("completed")
        ), userId, seriesId);
    }

    public Optional<SeriesDtos.PlayableVideo> findPlayableVideo(Long userId, Long videoId) {
        List<SeriesDtos.PlayableVideo> rows = jdbc.query("""
                SELECT v.id, c.series_id, v.title, v.video_key
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                JOIN t_student_series ss ON ss.series_id = c.series_id AND ss.user_id = ?
                JOIN t_series s ON s.id = c.series_id
                WHERE v.id = ? AND ss.status = 'ACTIVE'
                  AND (ss.valid_to IS NULL OR ss.valid_to > CURRENT_TIMESTAMP(3))
                """, (rs, rowNum) -> new SeriesDtos.PlayableVideo(
                rs.getLong("id"),
                rs.getLong("series_id"),
                rs.getString("title"),
                rs.getString("video_key")
        ), userId, videoId);
        return rows.stream().findFirst();
    }

    public record SeriesHeader(Long id, String title, String summary, String teacherName) {
    }

    public record ChapterRow(Long id, String title) {
    }

    public record VideoRow(Long id, Long chapterId, String title, int durationSeconds,
                           int positionSeconds, boolean completed) {
    }
}
