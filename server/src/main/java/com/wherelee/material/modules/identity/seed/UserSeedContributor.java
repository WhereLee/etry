package com.wherelee.material.modules.identity.seed;

import com.wherelee.material.modules.platform.config.SeedProperties;
import com.wherelee.material.modules.platform.seed.SeedContext;
import com.wherelee.material.modules.platform.seed.SeedContributor;
import com.wherelee.material.modules.platform.seed.SeedPasswords;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 身份模块种子：创建演示账号（t_user 归属 identity），并把 userId 注册进上下文供后续贡献者使用。
 */
@Slf4j
@Component
public class UserSeedContributor implements SeedContributor {

    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seed;

    public UserSeedContributor(PasswordEncoder passwordEncoder, SeedProperties seed) {
        this.passwordEncoder = passwordEncoder;
        this.seed = seed;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void contribute(SeedContext context) {
        context.putUserId("admin", seedUser(context, "admin", seed.adminPassword(), "管理员", "ADMIN"));
        context.putUserId("teacher", seedUser(context, "teacher", seed.teacherPassword(), "物理老师", "TEACHER"));
        context.putUserId("student", seedUser(context, "student", seed.studentPassword(), "演示学生", "STUDENT"));
    }

    private Long seedUser(SeedContext context, String username, String configuredPassword,
                          String nickname, String role) {
        JdbcTemplate jdbc = context.jdbc();
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE username = ?", Integer.class, username);
        if (existing == null || existing == 0) {
            boolean generated = configuredPassword == null || configuredPassword.isBlank();
            String password = generated ? SeedPasswords.generatePassword() : configuredPassword;
            jdbc.update("""
                    INSERT INTO t_user
                        (username, password_hash, nickname, role, status, must_change_password, token_version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', TRUE, 0)
                    """, username, passwordEncoder.encode(password), nickname, role);
            if (generated) {
                log.warn("初始化账号 username={} role={} 随机初始密码={}（仅本次启动打印，首次登录必须改密）",
                        username, role, password);
            } else {
                log.info("创建账号 username={} role={}，初始密码来自配置，首次登录必须改密", username, role);
            }
        }
        return jdbc.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }
}