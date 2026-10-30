-- M0 基础表结构
-- 命名约定：统一 t_ 前缀，避免 order 等关键字问题；金额/积分统一 BIGINT，时间统一 DATETIME(3)

CREATE TABLE t_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    nickname      VARCHAR(50)  NOT NULL COMMENT '昵称',
    role          VARCHAR(20)  NOT NULL COMMENT '角色 USER/ADMIN',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

CREATE TABLE t_product
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(100) NOT NULL COMMENT '商品名',
    description  VARCHAR(500) NULL COMMENT '描述',
    category     VARCHAR(50)  NOT NULL COMMENT '分类',
    price_points BIGINT       NOT NULL COMMENT '单价（积分）',
    stock_total  INT          NOT NULL COMMENT '总库存',
    stock_frozen INT          NOT NULL DEFAULT 0 COMMENT '冻结库存，可售 = total - frozen',
    status       VARCHAR(20)  NOT NULL COMMENT 'ON_SALE/OFF_SHELF',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_category_status (category, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品';

CREATE TABLE t_order
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32) NOT NULL COMMENT '订单号',
    user_id         BIGINT      NOT NULL COMMENT '下单用户',
    total_points    BIGINT      NOT NULL COMMENT '订单总积分',
    status          VARCHAR(20) NOT NULL COMMENT 'M0 仅 PENDING_PAY',
    address         VARCHAR(200) NULL COMMENT '收货地址快照',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '幂等键，唯一',
    expire_at       DATETIME(3) NOT NULL COMMENT '支付截止时间',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_user_idem (user_id, idempotency_key),
    KEY idx_order_user_status (user_id, status),
    KEY idx_order_expire (expire_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单';

CREATE TABLE t_order_item
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT       NOT NULL COMMENT '订单ID',
    product_id      BIGINT       NOT NULL COMMENT '商品ID',
    product_name    VARCHAR(100) NOT NULL COMMENT '下单时商品名快照',
    unit_points     BIGINT       NOT NULL COMMENT '下单时单价快照',
    quantity        INT          NOT NULL COMMENT '数量',
    subtotal_points BIGINT       NOT NULL COMMENT '小计',
    PRIMARY KEY (id),
    KEY idx_item_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细';

CREATE TABLE t_point_account
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '用户ID',
    balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '可用余额',
    frozen     BIGINT      NOT NULL DEFAULT 0 COMMENT '冻结余额',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='积分账户';

CREATE TABLE t_point_flow
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT      NOT NULL COMMENT '用户ID',
    order_id      BIGINT      NULL COMMENT '关联订单，可空',
    type          VARCHAR(20) NOT NULL COMMENT 'RECHARGE/FREEZE/DEDUCT/UNFREEZE/REFUND',
    amount        BIGINT      NOT NULL COMMENT '变动额，正负表示方向',
    balance_after BIGINT      NOT NULL COMMENT '变动后可用余额',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_flow_user_time (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='积分流水';

CREATE TABLE t_stock_log
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id BIGINT      NOT NULL COMMENT '商品ID',
    order_id   BIGINT      NULL COMMENT '关联订单，可空',
    type       VARCHAR(20) NOT NULL COMMENT 'RESERVE/RELEASE/DEDUCT/RETURN',
    quantity   INT         NOT NULL COMMENT '变动数量',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_stock_product_time (product_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='库存变动流水';
