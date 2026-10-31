-- 管理员开号 / 管理员重置密码后，用户下次登录必须先改密
--
-- 背景：机构里的老师账号由管理员统一开通，初始密码是管理员口头或纸面告知的。
-- 不做这个标记的话，这个"众所周知的密码"会一直有效。
--
-- 值为 1 时，除「改密 / 看自己信息 / 登出」之外的接口一律拒绝，
-- 改密成功后由业务代码置回 0。
ALTER TABLE t_user
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1=下次登录必须改密' AFTER token_version;
