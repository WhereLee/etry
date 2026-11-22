package com.wherelee.material.modules.platform.seed;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 种子上下文：在贡献者之间共享已解析的身份与商品标识。
 * 身份贡献者先写入 userIds，课程贡献者注册 seriesIds，权益贡献者据此分配。
 */
public final class SeedContext {

    private final JdbcTemplate jdbc;
    private final Map<String, Long> userIds = new HashMap<>();
    private final List<Long> seriesIds = new ArrayList<>();

    public SeedContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public void putUserId(String username, Long id) {
        userIds.put(username, id);
    }

    public Long userId(String username) {
        return userIds.get(username);
    }

    public void addSeriesId(Long seriesId) {
        seriesIds.add(seriesId);
    }

    public List<Long> seriesIds() {
        return List.copyOf(seriesIds);
    }
}