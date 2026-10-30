package com.wherelee.edu.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限校验。比按 URL 前缀拦截更精确：
 * 新加一个接口只要标上注解，不用去改路由配置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限编码，如 user:manage */
    String value();
}
