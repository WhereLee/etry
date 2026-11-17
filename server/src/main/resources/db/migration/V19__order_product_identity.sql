-- 商品标识通用化（Phase 3）：t_order 增通用 product 引用，series 列保留为 deprecated 别名（回滚兼容）。
-- 只增列与索引，不删改旧列；旧 jar 回滚仍可启动（product 列可空，读侧 COALESCE 回退 series）。
ALTER TABLE t_order
    ADD COLUMN product_id BIGINT NULL AFTER series_title_snapshot,
    ADD COLUMN product_type VARCHAR(40) NULL AFTER product_id,
    ADD COLUMN product_title_snapshot VARCHAR(100) NULL AFTER product_type;

UPDATE t_order
SET product_id = series_id,
    product_type = 'COURSE_SERIES',
    product_title_snapshot = series_title_snapshot;

ALTER TABLE t_order ADD KEY idx_order_user_product (user_id, product_id, status);