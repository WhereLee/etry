package com.wherelee.edu.user;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class User {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    /** 头像对象键，实际访问 URL 由服务端拼装 */
    private String avatarKey;
    /** 个人简介 */
    private String bio;
    private String status;
    /** 令牌版本：改密码或强制下线时 +1，使此前签发的所有 token 立即失效 */
    private Integer tokenVersion;
    /** 1=下次登录必须先改密。管理员开号或重置密码后置 1，本人改密后置 0 */
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean needsPasswordChange() {
        return Boolean.TRUE.equals(mustChangePassword);
    }
}
