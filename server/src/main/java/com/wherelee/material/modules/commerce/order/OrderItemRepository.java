package com.wherelee.material.modules.commerce.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 批量按订单取明细，供订单列表一次查询避免 N+1。 */
    public Map<Long, List<OrderItemRecord>> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(orderIds.size(), "?"));
        return jdbc.query("""
                        SELECT * FROM t_order_item WHERE order_id IN (%s) ORDER BY id
                        """.formatted(placeholders), MAPPER, orderIds.toArray()).stream()
                .collect(Collectors.groupingBy(OrderItemRecord::orderId, LinkedHashMap::new, Collectors.toList()));
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}