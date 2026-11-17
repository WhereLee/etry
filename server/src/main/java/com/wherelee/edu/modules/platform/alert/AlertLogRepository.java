package com.wherelee.edu.modules.platform.alert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 告警日志仓储：只写 platform 归属的 t_alert_log。
 * 播放统计读已迁至 learning.api.PlaybackStatsQuery（platform 不依赖业务模块）。
 */
@Repository
public class AlertLogRepository {

    private final JdbcTemplate jdbc;

    public AlertLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String alertType, String severity, String message,
                       boolean delivered, String deliveryError) {
        jdbc.update("""
                INSERT INTO t_alert_log (alert_type, severity, message, delivered, delivery_error)
                VALUES (?, ?, ?, ?, ?)
                """, alertType, severity, message, delivered, deliveryError);
    }
}