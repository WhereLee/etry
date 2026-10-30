-- ============================================================
-- 网课平台基座 · 初始表结构
--
-- 通用约定：
--   1. 主键统一 BIGINT AUTO_INCREMENT：InnoDB 聚簇索引按主键有序存放数据，
--      自增能保证顺序插入，避免随机插入导致的页分裂。
--   2. 时间统一 DATETIME(3)，保留毫秒，够定位同一秒内的先后。
--   3. 联合索引一律「等值字段在前、排序列在后」，这样能同时吃到过滤和排序。
--   4. 低选择性字段（status 只有两三个值）不单独建索引，走索引不如全表扫。
-- ============================================================


-- ------------------------------------------------------------
-- 一、身份与权限（RBAC 五表）
-- ------------------------------------------------------------

CREATE TABLE t_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    nickname      VARCHAR(50)  NOT NULL COMMENT '昵称',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 正常 / DISABLED 禁用',
    token_version INT          NOT NULL DEFAULT 0 COMMENT '令牌版本，改密或强退时 +1',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 登录按 username 等值查，唯一索引同时承担约束
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

CREATE TABLE t_role
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(30)  NOT NULL COMMENT '角色编码 STUDENT/TEACHER/ADMIN',
    name        VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(200) NULL COMMENT '说明',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色';

CREATE TABLE t_permission
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50) NOT NULL COMMENT '权限编码，如 user:manage',
    name       VARCHAR(50) NOT NULL COMMENT '权限名称',
    module     VARCHAR(30) NOT NULL COMMENT '所属模块，前端按模块分组展示',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code),
    KEY idx_permission_module (module)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权限点';

CREATE TABLE t_user_role
(
    user_id    BIGINT      NOT NULL COMMENT '用户ID',
    role_id    BIGINT      NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    -- 主键 (user_id, role_id)：支持「查某人的所有角色」（最左列 user_id）
    PRIMARY KEY (user_id, role_id),
    -- 单独建 role_id 索引：否则「查某角色下的所有人」用不上主键的最左前缀
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户-角色';

CREATE TABLE t_role_permission
(
    role_id       BIGINT      NOT NULL COMMENT '角色ID',
    permission_id BIGINT      NOT NULL COMMENT '权限ID',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_perm_permission (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色-权限';


-- ------------------------------------------------------------
-- 二、身份档案：讲师与学科
-- ------------------------------------------------------------

CREATE TABLE t_teacher_profile
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '关联用户',
    real_name     VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    title         VARCHAR(50)  NULL COMMENT '头衔，如「高级讲师」',
    bio           VARCHAR(500) NULL COMMENT '简介',
    avatar_key    VARCHAR(255) NULL COMMENT '头像对象键',
    years         INT          NOT NULL DEFAULT 0 COMMENT '教龄（年）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审 / APPROVED 通过 / REJECTED 驳回',
    reject_reason VARCHAR(200) NULL COMMENT '驳回原因',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 一个人只能有一份讲师档案
    UNIQUE KEY uk_teacher_user (user_id),
    -- 审核列表按状态筛
    KEY idx_teacher_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='讲师档案';

CREATE TABLE t_subject
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(30) NOT NULL COMMENT '学科编码',
    name       VARCHAR(50) NOT NULL COMMENT '学科名称',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='学科';

CREATE TABLE t_teacher_subject
(
    teacher_id BIGINT      NOT NULL COMMENT '讲师档案ID',
    subject_id BIGINT      NOT NULL COMMENT '学科ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    -- 查「某老师教什么」走主键最左列
    PRIMARY KEY (teacher_id, subject_id),
    -- 查「某学科有哪些老师」需要单独的 subject_id 索引
    KEY idx_ts_subject (subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='讲师-学科';


-- ------------------------------------------------------------
-- 三、内容与运营
-- ------------------------------------------------------------

CREATE TABLE t_notice
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(100) NOT NULL COMMENT '标题',
    content      TEXT         NOT NULL COMMENT '正文',
    publisher_id BIGINT       NOT NULL COMMENT '发布人',
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下架',
    is_top       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    published_at DATETIME(3)  NULL COMMENT '发布时间',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 前台列表固定查询：status = 'PUBLISHED' ORDER BY is_top DESC, created_at DESC
    -- 三个字段正好覆盖「过滤 + 两级排序」，不需要额外 filesort
    KEY idx_notice_status_top_time (status, is_top, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='公告';

CREATE TABLE t_message
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    receiver_id BIGINT       NOT NULL COMMENT '收件人',
    sender_id   BIGINT       NULL COMMENT '发件人，系统消息可为空',
    title       VARCHAR(100) NOT NULL COMMENT '标题',
    content     TEXT         NOT NULL COMMENT '正文',
    is_read     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读',
    read_at     DATETIME(3)  NULL COMMENT '阅读时间',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 收件箱固定查询：receiver_id = ? AND is_read = 0 ORDER BY created_at DESC
    -- 等值两列在前、排序列在后，一条索引同时解决过滤和排序
    KEY idx_msg_receiver_read_time (receiver_id, is_read, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='站内信';

CREATE TABLE t_config
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(50)  NOT NULL COMMENT '配置键',
    config_value VARCHAR(500) NOT NULL COMMENT '配置值',
    remark       VARCHAR(200) NULL COMMENT '说明',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统配置';

CREATE TABLE t_audit_log
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NULL COMMENT '操作人ID',
    username    VARCHAR(50)  NULL COMMENT '操作人登录名（冗余，避免关联查询）',
    action      VARCHAR(50)  NOT NULL COMMENT '动作编码，如 USER_BAN',
    target_type VARCHAR(30)  NULL COMMENT '目标类型，如 USER',
    target_id   BIGINT       NULL COMMENT '目标ID',
    detail      VARCHAR(500) NULL COMMENT '详情，参数摘要',
    ip          VARCHAR(50)  NULL COMMENT '来源IP',
    success     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功',
    cost_ms     INT          NULL COMMENT '耗时（毫秒）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 「某人做过什么」：user_id 等值 + 时间倒序
    KEY idx_audit_user_time (user_id, created_at),
    -- 「某类动作最近发生了什么」：action 等值 + 时间倒序
    KEY idx_audit_action_time (action, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作审计日志';
