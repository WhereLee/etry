package com.wherelee.material.modules.commerce.cart;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public class CartRepository {

    private static final RowMapper<CartItemRecord> MAPPER = (rs, rowNum) -> new CartItemRecord(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("material_id"),
            toLocal(rs.getTimestamp("created_at"))
    );

    private final JdbcTemplate jdbc;

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 幂等加购：重复加购返回 false，不抛错。 */
    public boolean add(long userId, long materialId) {
        return jdbc.update("""
                INSERT IGNORE INTO t_cart_item (user_id, material_id) VALUES (?, ?)
                """, userId, materialId) > 0;
    }

    public boolean exists(long userId, long materialId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM t_cart_item WHERE user_id = ? AND material_id = ?
                """, Integer.class, userId, materialId);
        return count != null && count > 0;
    }

    public List<CartItemRecord> findByUser(long userId) {
        return jdbc.query("""
                SELECT * FROM t_cart_item WHERE user_id = ? ORDER BY id DESC
                """, MAPPER, userId);
    }

    public int remove(long userId, long materialId) {
        return jdbc.update("""
                DELETE FROM t_cart_item WHERE user_id = ? AND material_id = ?
                """, userId, materialId);
    }

    /** 结算后移除已下单行。 */
    public int removeByUserAndMaterials(long userId, Collection<Long> materialIds) {
        if (materialIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", materialIds.stream().map(i -> "?").toList());
        Object[] args = new Object[materialIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (Long materialId : materialIds) {
            args[i++] = materialId;
        }
        return jdbc.update("""
                DELETE FROM t_cart_item WHERE user_id = ? AND material_id IN (""" + placeholders + ")",
                args);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}