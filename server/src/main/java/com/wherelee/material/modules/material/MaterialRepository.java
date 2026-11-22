package com.wherelee.material.modules.material;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class MaterialRepository {

    private static final RowMapper<MaterialRecord> MAPPER = (rs, rowNum) -> new MaterialRecord(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("category"),
            rs.getString("stage"),
            rs.getString("subject"),
            rs.getString("summary"),
            rs.getLong("price_cents"),
            rs.getString("currency"),
            rs.getInt("page_count"),
            rs.getInt("preview_pages"),
            rs.getString("cover_key"),
            rs.getString("file_key"),
            rs.getString("sale_status"),
            toLocal(rs.getTimestamp("sale_updated_at")),
            toLocal(rs.getTimestamp("created_at")),
            toLocal(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;

    public MaterialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MaterialRecord> findById(Long id) {
        return jdbc.query("SELECT * FROM t_material WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public List<MaterialRecord> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        return jdbc.query("SELECT * FROM t_material WHERE id IN (" + placeholders + ")",
                MAPPER, ids.toArray());
    }

    public List<MaterialRecord> listOnSale(String category, int limit, long offset) {
        if (category == null || category.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM t_material WHERE sale_status = 'ON_SALE'
                    ORDER BY id DESC LIMIT ? OFFSET ?
                    """, MAPPER, limit, offset);
        }
        return jdbc.query("""
                SELECT * FROM t_material WHERE sale_status = 'ON_SALE' AND category = ?
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, MAPPER, category, limit, offset);
    }

    public List<MaterialRecord> search(String keyword, int limit) {
        return jdbc.query("""
                SELECT * FROM t_material
                WHERE sale_status = 'ON_SALE'
                  AND MATCH(title, summary) AGAINST (? IN NATURAL LANGUAGE MODE)
                ORDER BY id DESC LIMIT ?
                """, MAPPER, keyword, limit);
    }

    public long countOnSale(String category) {
        Long count;
        if (category == null || category.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_material WHERE sale_status = 'ON_SALE'", Long.class);
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_material WHERE sale_status = 'ON_SALE' AND category = ?",
                    Long.class, category);
        }
        return count == null ? 0 : count;
    }

    public long insert(MaterialRecord material) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO t_material
                        (title, category, stage, subject, summary, price_cents, currency,
                         page_count, preview_pages, cover_key, file_key, sale_status, sale_updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, material.title());
            ps.setString(2, material.category());
            ps.setString(3, material.stage());
            ps.setString(4, material.subject());
            ps.setString(5, material.summary());
            ps.setLong(6, material.priceCents());
            ps.setString(7, material.currency());
            ps.setInt(8, material.pageCount());
            ps.setInt(9, material.previewPages());
            ps.setString(10, material.coverKey());
            ps.setString(11, material.fileKey());
            ps.setString(12, material.saleStatus());
            ps.setTimestamp(13, material.saleUpdatedAt() == null
                    ? null : Timestamp.valueOf(material.saleUpdatedAt()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int updateSaleStatus(long id, String saleStatus) {
        return jdbc.update("""
                UPDATE t_material SET sale_status = ?, sale_updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, saleStatus, id);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}