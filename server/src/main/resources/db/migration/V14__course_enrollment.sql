-- ============================================================
-- 学员与课程的关系 + 进度最远位置
--
-- 为什么要 enrollment：原来的"我的学习"是从学习进度反推的，
-- 只有真正播过视频的课才会出现，用户"想学但还没开始"这个决定无处存放。
-- 加上这张表之后，一门课对一个学员有四个状态：
--   JOINED     已加入，还没开始播
--   LEARNING   在学
--   COMPLETED  所有有视频课时都学完
--   QUIT       用户主动移出，记录保留，进度不清
--
-- lesson_count_snapshot 记的是"上次看到的课时数"，用来感知讲师增删课时。
-- ============================================================

CREATE TABLE t_course_enrollment
(
    id                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id               BIGINT      NOT NULL COMMENT '学员',
    course_id             BIGINT      NOT NULL COMMENT '课程',
    status                VARCHAR(20) NOT NULL DEFAULT 'JOINED' COMMENT 'JOINED/LEARNING/COMPLETED/QUIT',
    lesson_count_snapshot INT         NOT NULL DEFAULT 0 COMMENT '上次看到的课时数',
    joined_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    last_active_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近学习时间',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 一人一课只有一条关系，加入是幂等的
    UNIQUE KEY uk_enroll_user_course (user_id, course_id),
    -- "我的学习"按最近活跃倒序
    KEY idx_enroll_user_active (user_id, last_active_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='学员与课程的关系';

-- 存量回填：已经播过课的学员，不能因为换了数据源就从"我的学习"里消失
INSERT INTO t_course_enrollment (user_id, course_id, status, joined_at, last_active_at)
SELECT p.user_id, p.course_id, 'LEARNING', MIN(p.created_at), MAX(p.updated_at)
FROM t_learning_progress p
GROUP BY p.user_id, p.course_id;

-- 回填之后，把已经全部学完的标成 COMPLETED
UPDATE t_course_enrollment e
    JOIN (SELECT p.user_id,
                 p.course_id,
                 COUNT(CASE WHEN p.completed = 1 THEN 1 END) AS done_cnt
          FROM t_learning_progress p
          GROUP BY p.user_id, p.course_id) x
    ON x.user_id = e.user_id AND x.course_id = e.course_id
    JOIN (SELECT l.course_id, COUNT(*) AS total_cnt
          FROM t_course_lesson l
          WHERE l.video_key IS NOT NULL
            AND l.video_key != ''
          GROUP BY l.course_id) t
    ON t.course_id = e.course_id
SET e.status = 'COMPLETED'
WHERE t.total_cnt > 0
  AND x.done_cnt >= t.total_cnt;

-- ============================================================
-- 最远到达位置
--
-- position_seconds 的语义是"最后播到哪一秒"，用于下次续播；
-- 用户拖回开头看了一分钟，它就退回去了，这是对的。
-- 但完成判定和进度展示不能被回看影响，所以单独记一份只增不减的最远位置。
-- ============================================================

ALTER TABLE t_learning_progress
    ADD COLUMN furthest_seconds INT NOT NULL DEFAULT 0 COMMENT '到达过的最远位置，只增不减' AFTER position_seconds;

UPDATE t_learning_progress
SET furthest_seconds = position_seconds;

-- ============================================================
-- 站内信加跳转链接
-- 有人回答了你的提问，通知里要能一键回到那条问题。
-- ============================================================

ALTER TABLE t_message
    ADD COLUMN link VARCHAR(255) NULL COMMENT '点击后跳转的前端路径' AFTER content;
