package com.wherelee.material.modules.course.catalog;

import com.wherelee.material.modules.commerce.api.SellableCatalog;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 课程系列可售目录适配器：把 course 的 t_series 可售校验暴露为交易核心的通用商品端口。
 */
@Component
public class CourseSellableCatalog implements SellableCatalog {

    private final JdbcTemplate jdbc;

    public CourseSellableCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String productType() {
        return ProductTypes.COURSE_SERIES;
    }

    @Override
    public Optional<SellableProduct> findPurchasable(Long productId) {
        return jdbc.query("""
                SELECT id, title, price_cents, currency
                FROM t_series
                WHERE id = ? AND status = 'PUBLISHED'
                  AND sale_status = 'ON_SALE' AND price_cents > 0
                """, (rs, rowNum) -> new SellableProduct(
                rs.getLong("id"),
                ProductTypes.COURSE_SERIES,
                rs.getString("title"),
                rs.getLong("price_cents"),
                rs.getString("currency")
        ), productId).stream().findFirst();
    }
}