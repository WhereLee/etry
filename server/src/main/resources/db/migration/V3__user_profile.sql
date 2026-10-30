-- 用户自助资料：头像与个人简介
-- 头像只存对象键（本地存储时是相对路径），访问 URL 由服务端拼装，
-- 这样将来从本地存储切到对象存储时，只需改 URL 前缀，不用改数据。
ALTER TABLE t_user
    ADD COLUMN avatar_key VARCHAR(255) NULL COMMENT '头像对象键' AFTER nickname,
    ADD COLUMN bio VARCHAR(200) NULL COMMENT '个人简介' AFTER avatar_key;
