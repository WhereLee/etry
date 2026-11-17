package com.wherelee.edu.modules.course.catalog;

import com.wherelee.edu.modules.commerce.api.OrderQuery;
import com.wherelee.edu.modules.entitlement.api.EntitlementQuery;
import com.wherelee.edu.modules.entitlement.api.ProductTypes;
import com.wherelee.edu.modules.identity.api.UserDirectoryQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商店目录读模型门面（显式登记的跨模块读拼装）：
 * 课程展示字段（系列/讲师/章节/视频数）归属 course，讲师昵称走 identity 端口，
 * owned/activeOrder 交易事实走 entitlement/commerce 端口叠加。
 * 本门面是商店目录唯一的跨模块拼装点，写路径不得引用。
 */
@Component
public class StoreCatalogReadModel {

    private final JdbcTemplate jdbc;
    private final UserDirectoryQuery userDirectory;
    private final EntitlementQuery entitlementQuery;
    private final OrderQuery orderQuery;

    public StoreCatalogReadModel(JdbcTemplate jdbc,
                                 UserDirectoryQuery userDirectory,
                                 EntitlementQuery entitlementQuery,
                                 OrderQuery orderQuery) {
        this.jdbc = jdbc;
        this.userDirectory = userDirectory;
        this.entitlementQuery = entitlementQuery;
        this.orderQuery = orderQuery;
    }

    public List<CatalogItem> listForStudent(Long userId) {
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
        return rows.stream().map(row -> {
            boolean owned = entitlementQuery.hasActive(userId, row.id(), ProductTypes.COURSE_SERIES);
            OrderQuery.ActiveOrderFact active = orderQuery
                    .activeOrder(userId, row.id(), ProductTypes.COURSE_SERIES).orElse(null);
            return new CatalogItem(row.id(), row.title(), row.summary(),
                    nicknames.get(row.teacherId()), row.chapterCount(), row.videoCount(),
                    row.priceCents(), row.currency(), owned,
                    active == null ? null : active.orderNo(),
                    active == null ? null : active.status());
        }).toList();
    }

    private record Row(Long id, String title, String summary, Long teacherId,
                       long chapterCount, long videoCount, long priceCents, String currency) {
    }
}