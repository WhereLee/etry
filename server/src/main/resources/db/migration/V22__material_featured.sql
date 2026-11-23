-- 阶段6 运营端：首页推荐位（PRD §2 运营行）。featured=1 的资料在商城列表置顶。
ALTER TABLE t_material
    ADD COLUMN featured TINYINT(1) NOT NULL DEFAULT 0 COMMENT '首页推荐位 0/1' AFTER sale_status;