package com.wherelee.material.modules.commerce.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OrderItemRepository {

    private static final RowMapper<OrderItemRecord> MAPPER = (rs, rowNum) -> new OrderItemRecord(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getLong("material_id"),
            rs.getString("title_snapshot"),
            rs.getLong("price_cents"),
            toLocal(rs.getTimestamp("created_at"))
    );

    private final JdbcTemplate jdbc;

    public OrderItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(long orderId, List<OrderItemRecord> items) {
        jdbc.batchUpdate("""
                INSERT INTO t_order_item (order_id, material_id, title_snapshot, price_cents)
                VALUES (?, ?, ?, ?)
                """, items, items.size(), (ps, item) -> {
            ps.setLong(1, orderId);
            ps.setLong(2, item.materialId());
            ps.setString(3, item.titleSnapshot());
            ps.setLong(4, item.priceCents());
        });
    }

    public List<OrderItemRecord> findByOrderId(long orderId) {
        return jdbc.query("""
                SELECT * FROM t_order_item WHERE order_id = ? ORDER BY id
                """, MAPPER, orderId);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}