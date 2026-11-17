package com.wherelee.edu.modules.course.catalog;

import com.wherelee.edu.modules.commerce.api.CatalogDisplayQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import com.wherelee.edu.modules.identity.api.UserDirectoryQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 课程目录展示读模型：商店展示拼装（系列 + 章节/视频计数）归属 course。
 * 讲师昵称经 identity.api.UserDirectoryQuery 批量取，不再 join t_user；
 * owned/activeOrder 由 commerce 叠加。
 */
@Component
public class CourseCatalogDisplay implements CatalogDisplayQuery {

    private final JdbcTemplate jdbc;
    private final UserDirectoryQuery userDirectory;

    public CourseCatalogDisplay(JdbcTemplate jdbc, UserDirectoryQuery userDirectory) {
        this.jdbc = jdbc;
        this.userDirectory = userDirectory;
    }

    @Override
    public String productType() {
        return ProductTypes.COURSE_SERIES;
    }

    @Override
    public List<DisplayProduct> listOnSale() {
        List<Row> rows = jdbc.query("""
                SELECT s.id,
                       s.title,
                       s.summary,
                       s.teacher_id,
                       (SELECT COUNT(*) FROM t_series_chapter c WHERE c.series_id = s.id) AS chapter_count,
                       (SELECT COUNT(*) FROM t_series_video v
                        JOIN t_series_chapter c2 ON c2.id = v.chapter_id
                        WHERE c2.series_id = s.id) AS video_count,
                       s.price_cents,
                       s.currency
                FROM t_series s
                WHERE s.status = 'PUBLISHED'
                  AND s.sale_status = 'ON_SALE'
                  AND s.price_cents > 0
                ORDER BY s.sort, s.id
                """, (rs, rowNum) -> new Row(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getLong("teacher_id"),
                rs.getLong("chapter_count"),
                rs.getLong("video_count"),
                rs.getLong("price_cents"),
                rs.getString("currency")));
        Map<Long, String> nicknames = userDirectory.nicknamesById(rows.stream().map(Row::teacherId).toList());
        return rows.stream().map(row -> new DisplayProduct(
                row.id(), row.title(), row.summary(),
                nicknames.get(row.teacherId()),
                row.chapterCount(), row.videoCount(), row.priceCents(), row.currency())).toList();
    }

    private record Row(Long id, String title, String summary, Long teacherId,
                       long chapterCount, long videoCount, long priceCents, String currency) {
    }
}