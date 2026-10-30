-- ============================================================
-- 种子数据：角色、权限、学科、系统配置
--
-- 管理员账号不在这里：BCrypt 哈希必须在运行时生成，
-- 由 DataInitializer 在首次启动时创建（admin / admin123）。
-- ============================================================

INSERT INTO t_role (code, name, description)
VALUES ('STUDENT', '学员', '浏览课程、购买、学习'),
       ('TEACHER', '讲师', '发布和管理自己的课程'),
       ('ADMIN', '管理员', '管理用户、角色、公告等后台功能');

INSERT INTO t_permission (code, name, module)
VALUES ('user:manage', '用户管理', 'user'),
       ('role:manage', '角色权限管理', 'role'),
       ('teacher:review', '讲师审核', 'teacher'),
       ('subject:manage', '学科管理', 'subject'),
       ('notice:manage', '公告管理', 'notice'),
       ('message:send', '发送站内信', 'message'),
       ('config:manage', '系统配置', 'config');

-- 管理员拥有全部权限；学员和讲师暂无后台权限，等课程业务落地后再补
INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r
         CROSS JOIN t_permission p
WHERE r.code = 'ADMIN';

INSERT INTO t_subject (code, name, sort)
VALUES ('programming', '编程开发', 10),
       ('design', '设计', 20),
       ('language', '外语', 30),
       ('certificate', '职业考证', 40);

INSERT INTO t_config (config_key, config_value, remark)
VALUES ('site.name', '网课平台', '站点名称'),
       ('site.contact', '', '客服联系方式'),
       ('register.enabled', 'true', '是否开放注册'),
       ('notice.banner.enabled', 'true', '是否在首页展示公告栏');
