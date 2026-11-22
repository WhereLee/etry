-- 资料商城 v1 数据模型：资料目录、购物车、订单明细、RBAC。
-- t_order 单商品列（series_*）放宽为可空：购物车订单的商品行在 t_order_item，
-- price_cents 语义为订单总额；旧单商品订单数据不变，内核读侧 COALESCE 兼容。

CREATE TABLE t_material
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    title         VARCHAR(120)  NOT NULL,
    category      VARCHAR(20)   NOT NULL COMMENT 'EXAM_PAPER/TEACHING_AID/LECTURE_NOTES/TEXTBOOK',
    stage         VARCHAR(20)   NOT NULL DEFAULT 'GENERAL' COMMENT 'PRIMARY/JUNIOR/SENIOR/GENERAL',
    subject       VARCHAR(30)   NOT NULL DEFAULT '',
    summary       VARCHAR(500)  NOT NULL DEFAULT '',
    price_cents   BIGINT        NOT NULL,
    currency      VARCHAR(3)    NOT NULL DEFAULT 'CNY',
    page_count    INT           NOT NULL DEFAULT 0,
    preview_pages INT           NOT NULL DEFAULT 3,
    cover_key     VARCHAR(200)  NULL,
    file_key      VARCHAR(200)  NULL,
    sale_status   VARCHAR(20)   NOT NULL DEFAULT 'OFF_SALE' COMMENT 'ON_SALE/OFF_SALE',
    sale_updated_at DATETIME(3) NULL,
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_material_sale (sale_status, category, price_cents),
    FULLTEXT KEY ft_material (title, summary) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='电子资料目录';

CREATE TABLE t_cart_item
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    material_id BIGINT      NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_material (user_id, material_id),
    KEY idx_cart_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='购物车（电子资料数量恒1，不建数量列）';

CREATE TABLE t_order_item
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    material_id    BIGINT       NOT NULL,
    title_snapshot VARCHAR(120) NOT NULL,
    price_cents    BIGINT       NOT NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item (order_id, material_id),
    KEY idx_order_item_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细（标题/价格快照）';

ALTER TABLE t_order
    MODIFY COLUMN series_id BIGINT NULL,
    MODIFY COLUMN series_title_snapshot VARCHAR(100) NULL;

CREATE TABLE t_role
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(40)  NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    data_scope VARCHAR(20)  NOT NULL DEFAULT 'ALL' COMMENT 'SELF/ALL',
    built_in   TINYINT(1)   NOT NULL DEFAULT 0,
    remark     VARCHAR(200) NOT NULL DEFAULT '',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RBAC 角色';

CREATE TABLE t_permission
(
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    code   VARCHAR(80) NOT NULL,
    name   VARCHAR(80) NOT NULL,
    module VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RBAC 权限点';

CREATE TABLE t_role_permission
(
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色-权限点';

CREATE TABLE t_user_role
(
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    granted_by BIGINT      NULL,
    granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户-角色';

INSERT INTO t_role (code, name, data_scope, built_in, remark) VALUES
    ('BUYER', '买家', 'SELF', 1, '注册买家，仅本人数据'),
    ('OPERATOR', '运营', 'ALL', 1, '资料上下架与目录维护'),
    ('CUSTOMER_SERVICE', '客服', 'ALL', 1, '订单/退款查询与处理'),
    ('FINANCE', '财务', 'ALL', 1, '对账与退款复核'),
    ('SUPER_ADMIN', '超级管理员', 'ALL', 1, '账号权限与系统管理');