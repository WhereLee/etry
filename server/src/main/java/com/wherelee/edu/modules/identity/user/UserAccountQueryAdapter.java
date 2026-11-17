package com.wherelee.edu.modules.identity.user;

import com.wherelee.edu.modules.identity.api.UserAccountQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理台账号读适配器：只读 identity 自己归属的 t_user。
 * 过滤语义与原管理台查询一致：关键字模糊命中用户名/昵称，role/status 精确匹配，按 id 倒序。
 */
@Component
public class UserAccountQueryAdapter implements UserAccountQuery {

    private static final RowMapper<UserRow> MAPPER = (rs, rowNum) -> new UserRow(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getBoolean("must_change_password"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public UserAccountQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserRow> page(String keyword, String role, String status, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String where = filters(keyword, role, status, args);
        args.add(size);
        args.add(offset);
        return jdbc.query("""
                SELECT id, username, nickname, role, status, must_change_password, created_at
                FROM t_user
                """ + where + """
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, MAPPER, args.toArray());
    }

    @Override
    public long count(String keyword, String role, String status) {
        List<Object> args = new ArrayList<>();
        String where = filters(keyword, role, status, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_user" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private String filters(String keyword, String role, String status, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (keyword != null && !keyword.isEmpty()) {
            where.append(" WHERE (username LIKE ? OR nickname LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (role != null && !role.isEmpty()) {
            where.append(where.isEmpty() ? " WHERE" : " AND").append(" role = ?");
            args.add(role);
        }
        if (status != null && !status.isEmpty()) {
            where.append(where.isEmpty() ? " WHERE" : " AND").append(" status = ?");
            args.add(status);
        }
        return where.toString();
    }
}