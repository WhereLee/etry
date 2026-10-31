-- 运营统计的权限点。
-- 单独给一个，不复用 course:review：看板看的是全站数据，
-- 和「审核课程」不是一回事，将来可能只给运营不给审核。
INSERT INTO t_permission (code, name, module)
VALUES ('stats:view', '运营看板', 'stats');

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r,
     t_permission p
WHERE r.code = 'ADMIN'
  AND p.code = 'stats:view';
