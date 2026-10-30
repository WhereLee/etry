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
    private String status;
    /** 令牌版本：改密码或强制下线时 +1，使此前签发的所有 token 立即失效 */
    private Integer tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
