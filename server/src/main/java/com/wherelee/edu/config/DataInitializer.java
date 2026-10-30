package com.wherelee.edu.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建初始管理员。
 * 密码哈希不能写进 SQL 种子文件——BCrypt 每次加盐结果不同，只能在运行时生成。
 *
 * 初始密码优先取环境变量 ADMIN_INIT_PASSWORD；未设置时用内置默认值，
 * 但只在日志里提示「用的是默认密码」，绝不把密码本身写进日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD_ENV = "ADMIN_INIT_PASSWORD";
    private static final String FALLBACK_PASSWORD = "admin123";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user", Integer.class);
        if (userCount != null && userCount > 0) {
            return;
        }

        String password = environment.getProperty(ADMIN_PASSWORD_ENV);
        boolean usingFallback = password == null || password.isBlank();
        if (usingFallback) {
            password = FALLBACK_PASSWORD;
        }

        jdbcTemplate.update(
                "INSERT INTO t_user (username, password_hash, nickname, status, token_version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 0)",
                ADMIN_USERNAME, passwordEncoder.encode(password), "管理员");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_user WHERE username = ?", Long.class, ADMIN_USERNAME);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_role WHERE code = 'ADMIN'", Long.class);
        jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);

        if (usingFallback) {
            log.warn("已创建初始管理员 username={}，使用的是未设置 {} 时的默认密码，请立即登录修改",
                    ADMIN_USERNAME, ADMIN_PASSWORD_ENV);
        } else {
            log.info("已创建初始管理员 username={}，初始密码取自 {}", ADMIN_USERNAME, ADMIN_PASSWORD_ENV);
        }
    }
}
