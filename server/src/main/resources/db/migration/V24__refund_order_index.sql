-- 退款按订单查询（订单详情 / 客服处理 / 对账）缺少 order_id 索引：
-- V15 删除 uk_refund_order 后仅保留 uk_refund_attempt(attempt_id) 与 processing 索引，
-- 新式退款的 order_id 无索引，按订单查退款会全表扫描。
CREATE INDEX idx_refund_order ON t_refund (order_id);
