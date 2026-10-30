package com.wherelee.edu.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把当前登录身份直接注入到 Controller 参数上，省得每处都写 UserContext.requireUserId()。
 *
 * 支持两种类型：
 *   UserContext.Principal  → 完整身份（含角色）
 *   Long                   → 只取 userId
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
