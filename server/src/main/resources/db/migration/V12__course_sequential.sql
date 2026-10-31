-- ============================================================
-- 章节顺序学习 + 标签管理权限
--
-- sequential = 1 时，播放第 N 个课时前必须先完成第 N-1 个
-- （按 章节.sort, 课时.sort 的全课顺序）。
-- 默认关闭：强制顺序适合体系课，不适合当素材库用的课。
--
-- 顺带补 tag:manage 权限点：标签是课程元数据，
-- 和「审核课程」（course:review）是两件事，将来可能只给运营不给审核。
-- ============================================================

ALTER TABLE t_course
    ADD COLUMN sequential TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制按顺序学习' AFTER level;

INSERT INTO t_permission (code, name, module)
VALUES ('tag:manage', '标签管理', 'course');

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r,
     t_permission p
WHERE r.code = 'ADMIN'
  AND p.code = 'tag:manage';
