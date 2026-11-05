package com.wherelee.edu.modules.platform.config;

import com.wherelee.edu.modules.platform.storage.DemoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Slf4j
@Component
@Profile("!integration")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties demo;
    private final SeedProperties seed;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser("admin", seed.adminPassword(), "管理员", "ADMIN");
        Long teacherId = seedUser("teacher", seed.teacherPassword(), "物理老师", "TEACHER");
        Long studentId = seedUser("student", seed.studentPassword(), "演示学生", "STUDENT");

        if (count("t_series") == 0) {
            seedSeries(teacherId);
        }

        Long physics1 = idByTitle("物理必修一");
        Long physics2 = idByTitle("物理必修二");
        assign(studentId, physics1, 1);
        assign(studentId, physics2, 2);
        log.info("学习空间示例数据就绪 student={} series={},{}", studentId, physics1, physics2);
    }

    private Long seedUser(String username, String configuredPassword, String nickname, String role) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE username = ?", Integer.class, username);
        if (existing == null || existing == 0) {
            boolean generated = configuredPassword == null || configuredPassword.isBlank();
            String password = generated ? generatePassword() : configuredPassword;
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

    /**
     * 生成满足强密码规则的随机初始密码：16 位，至少一个字母与一个数字，
     * 排除容易混淆的 I/O/l/o/0/1，避免把固定口令写进仓库。
     */
    static String generatePassword() {
        char[] chars = new char[16];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        chars[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private void seedSeries(Long teacherId) {
        Long physics1 = insertSeries(teacherId, "物理必修一", "高中物理必修一 · 录播课程", 1);
        Long c11 = insertChapter(physics1, "第一章 运动的描述", 1);
        insertVideo(c11, "1.1 质点 参考系", 1);
        insertVideo(c11, "1.2 时间 位移", 2);
        insertVideo(c11, "1.3 位置变化快慢的描述——速度", 3);
        Long c12 = insertChapter(physics1, "第二章 匀变速直线运动", 2);
        insertVideo(c12, "2.1 实验：探究小车速度随时间变化的规律", 1);
        insertVideo(c12, "2.2 匀变速直线运动的速度与时间的关系", 2);
        insertVideo(c12, "2.3 匀变速直线运动的位移与时间的关系", 3);

        Long physics2 = insertSeries(teacherId, "物理必修二", "高中物理必修二 · 录播课程", 2);
        Long c21 = insertChapter(physics2, "第一章 抛体运动", 1);
        insertVideo(c21, "1.1 曲线运动", 1);
        insertVideo(c21, "1.2 运动的合成与分解", 2);
        insertVideo(c21, "1.3 实验：探究平抛运动的特点", 3);
        Long c22 = insertChapter(physics2, "第二章 圆周运动", 2);
        insertVideo(c22, "2.1 圆周运动", 1);
        insertVideo(c22, "2.2 向心力", 2);
        insertVideo(c22, "2.3 向心加速度", 3);
    }

    private Long insertSeries(Long teacherId, String title, String summary, int sort) {
        jdbc.update("""
                INSERT INTO t_series (teacher_id, title, summary, sort, status, published_at)
                VALUES (?, ?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP(3))
                """, teacherId, title, summary, sort);
        return jdbc.queryForObject("SELECT id FROM t_series WHERE title = ?", Long.class, title);
    }

    private Long insertChapter(Long seriesId, String title, int sort) {
        jdbc.update("""
                INSERT INTO t_series_chapter (series_id, title, sort)
                VALUES (?, ?, ?)
                """, seriesId, title, sort);
        return jdbc.queryForObject("""
                SELECT id FROM t_series_chapter WHERE series_id = ? AND title = ?
                """, Long.class, seriesId, title);
    }

    private void insertVideo(Long chapterId, String title, int sort) {
        jdbc.update("""
                INSERT INTO t_series_video (chapter_id, title, sort, video_key, duration_seconds)
                VALUES (?, ?, ?, ?, ?)
                """, chapterId, title, sort, demo.videoKey(), demo.videoDurationSeconds());
    }

    private void assign(Long studentId, Long seriesId, int sort) {
        if (studentId == null || seriesId == null) {
            return;
        }
        jdbc.update("""
                INSERT IGNORE INTO t_student_series (user_id, series_id, sort)
                VALUES (?, ?, ?)
                """, studentId, seriesId, sort);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private Long idByTitle(String title) {
        return jdbc.queryForObject("SELECT id FROM t_series WHERE title = ?", Long.class, title);
    }
}
