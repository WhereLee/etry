package com.wherelee.material.modules.material;

import com.wherelee.material.modules.commerce.api.SellableCatalog;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 电子资料可售目录适配器：上架、有定价且已挂文件才可售。
 */
@Component
public class MaterialSellableCatalog implements SellableCatalog {

    private final JdbcTemplate jdbc;

    public MaterialSellableCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String productType() {
        return ProductTypes.MATERIAL;
    }

    @Override
    public Optional<SellableProduct> findPurchasable(Long productId) {
        return jdbc.query("""
                SELECT id, title, price_cents, currency
                FROM t_material
                WHERE id = ? AND sale_status = 'ON_SALE'
                  AND price_cents > 0 AND file_key IS NOT NULL
                """, (rs, rowNum) -> new SellableProduct(
                rs.getLong("id"),
                ProductTypes.MATERIAL,
                rs.getString("title"),
                rs.getLong("price_cents"),
                rs.getString("currency")
        ), productId).stream().findFirst();
    }
}