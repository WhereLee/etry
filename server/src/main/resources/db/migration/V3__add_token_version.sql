-- 令牌版本：改密码或强制下线时 +1，使该用户此前签发的所有 JWT 立即失效。
-- 单个设备的退出走 Redis 黑名单，不依赖此字段。
ALTER TABLE t_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本，改密或强退时 +1' AFTER role;
