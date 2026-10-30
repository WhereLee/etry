package com.wherelee.trade.user;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class User {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String role;
    /** 令牌版本，改密码或强制下线时 +1，使旧 token 立即失效 */
    private Integer tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
