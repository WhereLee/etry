package com.wherelee.edu.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 注册相关的安全策略。
 *
 * revealUsernameConflict：注册时用户名被占用，是否明确告诉调用方「已存在」。
 * 关着的时候统一回「该用户名暂不可用」——登录接口特意把「账号不存在」和
 * 「密码错误」合并成同一个错误，注册这里不能重新把这个口子打开。
 *
 * 自助注册的总开关不在这里，在 t_config 的 register.enabled：
 * 那是运营配置，管理员能随时在后台改；这里是启动期的安全策略。
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(boolean revealUsernameConflict) {
}
