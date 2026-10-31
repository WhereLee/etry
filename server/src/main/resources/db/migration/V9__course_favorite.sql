-- ============================================================
-- 课程收藏
--
-- 唯一键 (user_id, course_id)：收藏是个开关，重复收藏按幂等处理，
-- 不需要应用层先查有没有再插入。
--
-- idx_favorite_user (user_id, created_at)：我的收藏按时间倒序，
-- 这个索引同时覆盖过滤和排序。
-- ============================================================

CREATE TABLE t_course_favorite
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '收藏人',
    course_id  BIGINT      NOT NULL COMMENT '课程',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_user_course (user_id, course_id),
    KEY idx_favorite_user (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程收藏';
