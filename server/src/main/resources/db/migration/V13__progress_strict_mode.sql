-- ============================================================
-- 进度严格模式的支撑字段 + 注册默认策略调整
--
-- 1) first_reported_at：严格模式下判断「从第一次上报到现在过了多久」。
--    完成判定从「位置到 90%」升级为「位置到 90% 且真实时间也过了 90% 的时长」。
--    宽松模式下这一列只写不读，开着不浪费。
--
-- 2) register.enabled 默认改为 false：
--    这是内部平台，默认走管理员开号；要放开自助注册在后台改这一条即可。
--    改默认值而不是删记录，是为了让运营能看到这个开关存在。
-- ============================================================

ALTER TABLE t_learning_progress
    ADD COLUMN first_reported_at DATETIME(3) NULL COMMENT '第一次上报时间，严格模式判完成用' AFTER completed;

UPDATE t_config
SET config_value = 'false'
WHERE config_key = 'register.enabled';
