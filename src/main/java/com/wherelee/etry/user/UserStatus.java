package com.wherelee.etry.user;

public enum UserStatus {
    /** 已注册但邮箱未验证，不能登录 */
    PENDING_VERIFY,
    /** 正常可用 */
    ACTIVE
}
