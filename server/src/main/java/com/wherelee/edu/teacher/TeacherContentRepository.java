package com.wherelee.edu.teacher;

import com.wherelee.edu.series.dto.SeriesDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TeacherContentRepository {

    private final JdbcTemplate jdbc;

    public TeacherContentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SeriesDtos.TeacherSeriesSummary> findSeries(Long teacherId) {
        return jdbc.query("""
                SELECT s.id, s.title, s.summary, s.status, s.updated_at,
                       COUNT(DISTINCT c.id) AS chapter_count,
                       COUNT(DISTINCT v.id) AS video_count
                FROM t_series s
                LEFT JOIN t_series_chapter c ON c.series_id = s.id
                LEFT JOIN t_series_video v ON v.chapter_id = c.id
                WHERE s.teacher_id = ?
                GROUP BY s.id, s.title, s.summary, s.status, s.updated_at, s.sort
                ORDER BY s.sort, s.id
                """, (rs, rowNum) -> new SeriesDtos.TeacherSeriesSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("status"),
                rs.getLong("chapter_count"),
                rs.getLong("video_count"),
                rs.getObject("updated_at", LocalDateTime.class)
        ), teacherId);
    }

    public Optional<SeriesHeader> findSeriesHeader(Long teacherId, Long seriesId) {
        List<SeriesHeader> rows = jdbc.query("""
                SELECT s.id, s.title, s.summary, s.status, s.sort, u.nickname AS teacher_name
                FROM t_series s
                JOIN t_user u ON u.id = s.teacher_id
                WHERE s.id = ? AND s.teacher_id = ?
                """, (rs, rowNum) -> new SeriesHeader(
                rs.getLong("id"), rs.getString("title"), rs.getString("summary"),
                rs.getString("status"), rs.getInt("sort"), rs.getString("teacher_name")
        ), seriesId, teacherId);
        return rows.stream().findFirst();
    }

    public List<ChapterRow> findChapters(Long seriesId) {
        return jdbc.query("""
                SELECT id, title, sort
                FROM t_series_chapter
                WHERE series_id = ?
                ORDER BY sort, id
                """, (rs, rowNum) -> new ChapterRow(
                rs.getLong("id"), rs.getString("title"), rs.getInt("sort")), seriesId);
    }

    public List<VideoRow> findVideos(Long seriesId) {
        return jdbc.query("""
                SELECT v.id, v.chapter_id, v.title, v.sort, v.video_key,
                       v.media_status, v.duration_seconds
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                WHERE c.series_id = ?
                ORDER BY c.sort, c.id, v.sort, v.id
                """, (rs, rowNum) -> new VideoRow(
                rs.getLong("id"), rs.getLong("chapter_id"), rs.getString("title"),
                rs.getInt("sort"), rs.getString("video_key"), rs.getString("media_status"),
                rs.getInt("duration_seconds")), seriesId);
    }

    public Long createSeries(Long teacherId, String title, String summary, int sort) {
        return insert("""
                INSERT INTO t_series (teacher_id, title, summary, sort, status)
                VALUES (?, ?, ?, ?, 'DRAFT')
                """, teacherId, title, summary, sort);
    }

    public int updateSeries(Long teacherId, Long seriesId, String title, String summary, int sort) {
        return jdbc.update("""
                UPDATE t_series SET title = ?, summary = ?, sort = ?
                WHERE id = ? AND teacher_id = ?
                """, title, summary, sort, seriesId, teacherId);
    }

    public int updateStatus(Long teacherId, Long seriesId, String status, LocalDateTime publishedAt) {
        return jdbc.update("""
                UPDATE t_series SET status = ?, published_at = ?
                WHERE id = ? AND teacher_id = ?
                """, status, publishedAt, seriesId, teacherId);
    }

    public Optional<OwnedSeries> findOwnedSeries(Long teacherId, Long seriesId) {
        return jdbc.query("""
                SELECT id, status FROM t_series WHERE id = ? AND teacher_id = ?
                """, (rs, rowNum) -> new OwnedSeries(rs.getLong("id"), rs.getString("status")),
                seriesId, teacherId).stream().findFirst();
    }

    public long countChapters(Long seriesId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_series_chapter WHERE series_id = ?", Long.class, seriesId);
        return value == null ? 0 : value;
    }

    public long countVideos(Long seriesId) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                WHERE c.series_id = ?
                """, Long.class, seriesId);
        return value == null ? 0 : value;
    }

    public long countPendingVideos(Long seriesId) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                WHERE c.series_id = ?
                  AND (v.media_status <> 'READY' OR v.video_key IS NULL OR v.video_key = '')
                """, Long.class, seriesId);
        return value == null ? 0 : value;
    }

    public Long createChapter(Long seriesId, String title, int sort) {
        return insert("""
                INSERT INTO t_series_chapter (series_id, title, sort)
                VALUES (?, ?, ?)
                """, seriesId, title, sort);
    }

    public int updateChapter(Long seriesId, Long chapterId, String title, int sort) {
        return jdbc.update("""
                UPDATE t_series_chapter SET title = ?, sort = ?
                WHERE id = ? AND series_id = ?
                """, title, sort, chapterId, seriesId);
    }

    public int deleteChapter(Long seriesId, Long chapterId) {
        return jdbc.update("""
                DELETE FROM t_series_chapter WHERE id = ? AND series_id = ?
                """, chapterId, seriesId);
    }

    public Optional<OwnedChapter> findOwnedChapter(Long teacherId, Long seriesId, Long chapterId) {
        return jdbc.query("""
                SELECT c.id, c.series_id
                FROM t_series_chapter c
                JOIN t_series s ON s.id = c.series_id
                WHERE c.id = ? AND c.series_id = ? AND s.teacher_id = ?
                """, (rs, rowNum) -> new OwnedChapter(
                rs.getLong("id"), rs.getLong("series_id")), chapterId, seriesId, teacherId)
                .stream().findFirst();
    }

    public long countVideosInChapter(Long chapterId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_series_video WHERE chapter_id = ?", Long.class, chapterId);
        return value == null ? 0 : value;
    }

    public Long createVideo(Long chapterId, String title, int sort, int durationSeconds) {
        return insert("""
                INSERT INTO t_series_video
                    (chapter_id, title, sort, video_key, media_status, duration_seconds)
                VALUES (?, ?, ?, NULL, 'PENDING', ?)
                """, chapterId, title, sort, durationSeconds);
    }

    public int updateVideo(Long chapterId, Long videoId, String title, int sort, int durationSeconds) {
        return jdbc.update("""
                UPDATE t_series_video
                SET title = ?, sort = ?, duration_seconds = ?
                WHERE id = ? AND chapter_id = ?
                """, title, sort, durationSeconds, videoId, chapterId);
    }

    public int deleteVideo(Long chapterId, Long videoId) {
        return jdbc.update("""
                DELETE FROM t_series_video WHERE id = ? AND chapter_id = ?
                """, videoId, chapterId);
    }

    public Optional<OwnedVideo> findOwnedVideo(Long teacherId, Long seriesId,
                                               Long chapterId, Long videoId) {
        return jdbc.query("""
                SELECT v.id, v.chapter_id
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                JOIN t_series s ON s.id = c.series_id
                WHERE v.id = ? AND v.chapter_id = ? AND c.series_id = ? AND s.teacher_id = ?
                """, (rs, rowNum) -> new OwnedVideo(
                rs.getLong("id"), rs.getLong("chapter_id")), videoId, chapterId, seriesId, teacherId)
                .stream().findFirst();
    }

    public Optional<VideoUploadRow> findVideoForUpload(Long teacherId, Long seriesId,
                                                       Long chapterId, Long videoId) {
        return jdbc.query("""
                SELECT v.id, v.chapter_id, v.video_key, v.pending_object_key,
                       v.media_status, v.content_type, v.duration_seconds
                FROM t_series_video v
                JOIN t_series_chapter c ON c.id = v.chapter_id
                JOIN t_series s ON s.id = c.series_id
                WHERE v.id = ? AND v.chapter_id = ? AND c.series_id = ? AND s.teacher_id = ?
                """, (rs, rowNum) -> new VideoUploadRow(
                rs.getLong("id"), rs.getLong("chapter_id"), rs.getString("video_key"),
                rs.getString("pending_object_key"), rs.getString("media_status"), rs.getString("content_type"),
                rs.getInt("duration_seconds")), videoId, chapterId, seriesId, teacherId)
                .stream().findFirst();
    }

    public int markUploading(Long chapterId, Long videoId, String objectKey,
                             String contentType, String originalFilename) {
        return jdbc.update("""
                UPDATE t_series_video
                SET pending_object_key = ?, media_status = 'UPLOADING',
                    content_type = ?, original_filename = ?, uploaded_at = NULL
                WHERE id = ? AND chapter_id = ?
                """, objectKey, contentType, originalFilename, videoId, chapterId);
    }

    public int markReady(Long chapterId, Long videoId, long fileSizeBytes,
                         String contentType, int durationSeconds, LocalDateTime uploadedAt) {
        return jdbc.update("""
                UPDATE t_series_video
                SET video_key = pending_object_key, pending_object_key = NULL,
                    media_status = 'READY', file_size_bytes = ?, content_type = ?,
                    duration_seconds = ?, uploaded_at = ?
                WHERE id = ? AND chapter_id = ?
                """, fileSizeBytes, contentType, durationSeconds, uploadedAt, videoId, chapterId);
    }

    private Long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("写入后未返回主键");
        }
        return key.longValue();
    }

    public record SeriesHeader(Long id, String title, String summary, String status,
                               int sort, String teacherName) {
    }

    public record ChapterRow(Long id, String title, int sort) {
    }

    public record VideoRow(Long id, Long chapterId, String title, int sort, String videoKey,
                           String mediaStatus, int durationSeconds) {
    }

    public record OwnedSeries(Long id, String status) {
    }

    public record OwnedChapter(Long id, Long seriesId) {
    }

    public record OwnedVideo(Long id, Long chapterId) {
    }

    public record VideoUploadRow(Long id, Long chapterId, String videoKey,
                                 String pendingObjectKey, String mediaStatus,
                                 String contentType, int durationSeconds) {
    }
}
