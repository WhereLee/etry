package com.wherelee.edu.modules.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首次启动创建演示账号时使用的初始密码。
 *
 * <p>留空则随机生成并在启动日志打印一次，避免把固定口令写进代码仓库。
 * 账号已存在时不会重新设置密码，改密只能走登录改密或管理员重置。</p>
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(
        String adminPassword,
        String teacherPassword,
        String studentPassword
) {
}
