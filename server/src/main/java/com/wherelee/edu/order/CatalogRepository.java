package com.wherelee.edu.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CatalogItem> findOnSale(Long userId) {
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
                       s.currency,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM t_student_series ss
                           WHERE ss.user_id = ? AND ss.series_id = s.id AND ss.status = 'ACTIVE'
                             AND (ss.valid_to IS NULL OR ss.valid_to > CURRENT_TIMESTAMP(3))
                       ) THEN 1 ELSE 0 END AS owned,
                       (SELECT o.order_no FROM t_order o
                        WHERE o.user_id = ? AND o.series_id = s.id
                          AND o.status IN ('CREATED', 'PAYING', 'PAY_FAILED', 'PAID')
                        ORDER BY o.id DESC LIMIT 1) AS active_order_no,
                       (SELECT o.status FROM t_order o
                        WHERE o.user_id = ? AND o.series_id = s.id
                          AND o.status IN ('CREATED', 'PAYING', 'PAY_FAILED', 'PAID')
                        ORDER BY o.id DESC LIMIT 1) AS active_order_status
                FROM t_series s
                JOIN t_user u ON u.id = s.teacher_id
                WHERE s.status = 'PUBLISHED'
                  AND s.sale_status = 'ON_SALE'
                  AND s.price_cents > 0
                ORDER BY s.sort, s.id
                """, (rs, rowNum) -> new CatalogItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("teacher_name"),
                rs.getLong("chapter_count"),
                rs.getLong("video_count"),
                rs.getLong("price_cents"),
                rs.getString("currency"),
                rs.getBoolean("owned"),
                rs.getString("active_order_no"),
                rs.getString("active_order_status")
        ), userId, userId, userId);
    }

    public Optional<SaleSeries> findPurchasable(Long seriesId) {
        return jdbc.query("""
                SELECT id, title, price_cents, currency
                FROM t_series
                WHERE id = ? AND status = 'PUBLISHED'
                  AND sale_status = 'ON_SALE' AND price_cents > 0
                """, (rs, rowNum) -> new SaleSeries(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getLong("price_cents"),
                rs.getString("currency")
        ), seriesId).stream().findFirst();
    }
}
