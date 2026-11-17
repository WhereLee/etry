package com.wherelee.edu.modules.course.catalog;

import com.wherelee.edu.modules.commerce.api.CatalogDisplayQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 课程目录展示读模型：商店展示拼装（系列 + 讲师 + 章节/视频计数）归属 course。
 * 跨读 t_user 取讲师昵称属读模型门面豁免；owned/activeOrder 由 commerce 叠加。
 */
@Component
public class CourseCatalogDisplay implements CatalogDisplayQuery {

    private final JdbcTemplate jdbc;

    public CourseCatalogDisplay(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String productType() {
        return ProductTypes.COURSE_SERIES;
    }

    @Override
    public List<DisplayProduct> listOnSale() {
        return jdbc.query("""
                SELECT s.id,
                       s.title,
                       s.summary,
                       u.nickname AS teacher_name,
                       (SELECT COUNT(*) FROM t_series_chapter c WHERE c.series_id = s.id) AS chapter_count,
                       (SELECT COUNT(*) FROM t_series_video v
                        JOIN t_series_chapter c2 ON c2.id = v.chapter_id
                        WHERE c2.series_id = s.id) AS video_count,
                       s.price_cents,
                       s.currency
                FROM t_series s
                JOIN t_user u ON u.id = s.teacher_id
                WHERE s.status = 'PUBLISHED'
                  AND s.sale_status = 'ON_SALE'
                  AND s.price_cents > 0
                ORDER BY s.sort, s.id
                """, (rs, rowNum) -> new DisplayProduct(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("teacher_name"),
                rs.getLong("chapter_count"),
                rs.getLong("video_count"),
                rs.getLong("price_cents"),
                rs.getString("currency")));
    }
}