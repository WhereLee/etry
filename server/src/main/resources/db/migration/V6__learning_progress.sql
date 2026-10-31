-- ============================================================
-- 学习进度
--
-- 一个用户对一个课时只有一条记录，所以唯一键是 (user_id, lesson_id)。
-- 上报走 INSERT ... ON DUPLICATE KEY UPDATE：一条 SQL 完成"有则更新、无则插入"，
-- 不做"先查再写"，避免两个请求同时上报时互相覆盖。
--
-- course_id 是冗余字段：按课程查进度时不用再 join 课时表。
-- 课时不会换课程，所以这个冗余不会失配。
--
-- position_seconds 存"播到第几秒"，不等于"看了多久"——
-- 用户拖回去重看，进度就该往回走，这是正常行为。
-- duration_seconds 取自课时表，不信任客户端上报的时长。
-- completed 只增不减：看到过结尾就算学过，回看不会把它清掉。
-- ============================================================

CREATE TABLE t_learning_progress
(
    id               BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT     NOT NULL COMMENT '学习者',
    course_id        BIGINT     NOT NULL COMMENT '冗余课程 ID，便于按课程聚合',
    lesson_id        BIGINT     NOT NULL COMMENT '课时',
    position_seconds INT        NOT NULL DEFAULT 0 COMMENT '播放到第几秒',
    duration_seconds INT        NOT NULL DEFAULT 0 COMMENT '课时总时长（秒），取课时表的值',
    completed        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否看完：播放过 90% 即视为完成',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 一个人一个课时只有一条：既是业务约束，也是 UPSERT 的落点
    UNIQUE KEY uk_progress_user_lesson (user_id, lesson_id),
    -- 查某门课的进度、以及"我的学习"按最近时间倒序，都靠这个
    KEY idx_progress_user_course (user_id, course_id, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='学习进度';
