-- ============================================================
-- 反馈回路：课程评价 + 课程问答
--
-- 为什么把这两块放一起：它们是同一个回路的两端——
--   评价：学完的人告诉后来的人「这课怎么样」
--   问答：卡住的人问，讲课的人答，后来的人能看到答案
-- 没有这两样，平台就只是文件分发，不是学习平台。
--
-- 评价用唯一键 (user_id, course_id) 约束「一人一课一条」，
-- 想改就打补丁，不允许刷多条拉高均分。
--
-- 问答拆两张表：问题可以被回答多次，回答不会脱离问题存在。
-- ============================================================

CREATE TABLE t_course_review
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id  BIGINT       NOT NULL COMMENT '课程',
    user_id    BIGINT       NOT NULL COMMENT '评价人',
    rating     TINYINT      NOT NULL COMMENT '评分 1-5',
    content    VARCHAR(500) NULL COMMENT '评价内容，可以只打分不写字',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 一人一课只能一条：既是业务约束，也是「重复提交就更新」的落点
    UNIQUE KEY uk_review_user_course (user_id, course_id),
    -- 课程详情按时间倒序列评价
    KEY idx_review_course (course_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程评价';

CREATE TABLE t_course_question
(
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id  BIGINT        NOT NULL COMMENT '课程',
    user_id    BIGINT        NOT NULL COMMENT '提问人',
    content    VARCHAR(1000) NOT NULL COMMENT '问题内容',
    resolved   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已解决',
    created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_question_course (course_id, created_at),
    -- 讲师工作台要按人统计「我提过多少」
    KEY idx_question_user (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程提问';

CREATE TABLE t_course_answer
(
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id BIGINT        NOT NULL COMMENT '所属问题',
    user_id     BIGINT        NOT NULL COMMENT '回答人',
    content     VARCHAR(1000) NOT NULL COMMENT '回答内容',
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_answer_question (question_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程回答';
