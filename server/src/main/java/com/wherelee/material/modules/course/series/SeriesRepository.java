package com.wherelee.material.modules.course.series;

import com.wherelee.material.modules.course.series.dto.SeriesDtos;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SeriesRepository {

    private final JdbcTemplate jdbc;
    private final EntitlementQuery entitlementQuery;

    public SeriesRepository(JdbcTemplate jdbc, EntitlementQuery entitlementQuery) {
        this.jdbc = jdbc;
        this.entitlementQuery = entitlementQuery;
    }

    public List<SeriesDtos.SeriesSummary> findForStudent(Long userId) {
        List<EntitlementQuery.ActiveProduct> active =
                entitlementQuery.listActive(userId, ProductTypes.COURSE_SERIES);
        if (active.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> displayOrder = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (EntitlementQuery.ActiveProduct product : active) {
            displayOrder.put(product.productId(), product.displayOrder());
            ids.add(product.productId());
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 1];
        args[0] = userId;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        List<SeriesDtos.SeriesSummary> rows = jdbc.query("""
                SELECT s.id,
                       s.title,
                       s.summary,
                       u.nickname AS teacher_name,
                       COUNT(DISTINCT c.id) AS chapter_count,
                       COUNT(DISTINCT v.id) AS video_count,
                       COUNT(DISTINCT CASE WHEN p.completed = 1 THEN v.id END) AS completed_video_count
                FROM t_series s
                JOIN t_user u ON u.id = s.teacher_id
                LEFT JOIN t_series_chapter c ON c.series_id = s.id
                LEFT JOIN t_series_video v ON v.chapter_id = c.id
                LEFT JOIN t_learning_progress p ON p.user_id = ? AND p.video_id = v.id
                WHERE s.id IN (%s)
                GROUP BY s.id, s.title, s.summary, u.nickname
                """.formatted(placeholders), (rs, rowNum) -> new SeriesDtos.SeriesSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("teacher_name"),
                rs.getLong("chapter_count"),
                rs.getLong("video_count"),
                rs.getLong("completed_video_count")
        ), args);
        return rows.stream()
                .sorted(Comparator.comparing(
                                (SeriesDtos.SeriesSummary row) -> displayOrder.getOrDefault(row.id(), Integer.MAX_VALUE))
                        .thenComparing(SeriesDtos.SeriesSummary::id))
                .toList();
    }

    public Optional<SeriesHeader> findAssignedSeries(Long userId, Long seriesId) {
        if (!entitlementQuery.hasActive(userId, seriesId, ProductTypes.COURSE_SERIES)) {
            return Optional.empty();
        }
        List<SeriesHeader> rows = jdbc.query("""
                SELECT s.id, s.title, s.summary, u.nickname AS teacher_name
                FROM t_series s
                JOIN t_user u ON u.id = s.teacher_id
                WHERE s.id = ?
                """, (rs, rowNum) -> new SeriesHeader(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("teacher_name")
        ), seriesId);
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
                WHERE v.id = ?
                """, (rs, rowNum) -> new SeriesDtos.PlayableVideo(
                rs.getLong("id"),
                rs.getLong("series_id"),
                rs.getString("title"),
                rs.getString("video_key")
        ), videoId);
        return rows.stream().findFirst()
                .filter(video -> entitlementQuery.hasActive(userId, video.seriesId(), ProductTypes.COURSE_SERIES));
    }

    public record SeriesHeader(Long id, String title, String summary, String teacherName) {
    }

    public record ChapterRow(Long id, String title) {
    }

    public record VideoRow(Long id, Long chapterId, String title, int durationSeconds,
                           int positionSeconds, boolean completed) {
    }
}