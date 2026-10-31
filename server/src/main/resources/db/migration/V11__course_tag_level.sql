-- ============================================================
-- 课程标签与难度
--
-- 标签是多对多：一门课可以有「零基础」「面试」「实战」多个标签，
-- 前置知识在课程一多就不够用了。
--
-- 难度用枚举存在课程表上而不是标签：
--   难度只能一个值，标签可以多个，塞在一起会让筛选逻辑变形。
--
-- t_course_tag 用 (course_id, tag_id) 做主键：
--   天然防重复，删课程时按 course_id 前缀删也走得上索引。
--   反向索引 idx_course_tag_tag 给「按标签筛课程」和「标签被引用多少次」用。
-- ============================================================

CREATE TABLE t_tag
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(30) NOT NULL COMMENT '标签名',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程标签';

CREATE TABLE t_course_tag
(
    course_id BIGINT NOT NULL COMMENT '课程',
    tag_id    BIGINT NOT NULL COMMENT '标签',
    PRIMARY KEY (course_id, tag_id),
    KEY idx_course_tag_tag (tag_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程-标签关联';

ALTER TABLE t_course
    ADD COLUMN level VARCHAR(20) NULL COMMENT '难度：BEGINNER / INTERMEDIATE / ADVANCED，可空' AFTER subject_id,
    ADD KEY idx_course_level (level);
