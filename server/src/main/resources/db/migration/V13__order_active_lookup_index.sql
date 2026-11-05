-- 订单创建/支付前会按 user_id + series_id + status 查找活跃订单。
-- 没有该复合索引时，MySQL 会反向扫描主键并在 1 万级订单上做全表过滤。
ALTER TABLE t_order
    ADD KEY idx_order_user_series_status (user_id, series_id, status, id);
