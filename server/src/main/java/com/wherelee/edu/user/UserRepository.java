package com.wherelee.edu.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getBoolean("must_change_password"),
            rs.getInt("token_version")
    );

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByUsername(String username) {
        List<User> users = jdbc.query("""
                SELECT id, username, password_hash, nickname, role, status,
                       must_change_password, token_version
                FROM t_user
                WHERE username = ?
                """, MAPPER, username);
        return users.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        List<User> users = jdbc.query("""
                SELECT id, username, password_hash, nickname, role, status,
                       must_change_password, token_version
                FROM t_user
                WHERE id = ?
                """, MAPPER, id);
        return users.stream().findFirst();
    }

    public void updatePassword(Long id, String passwordHash) {
        jdbc.update("""
                UPDATE t_user
                SET password_hash = ?,
                    must_change_password = 0,
                    token_version = token_version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, passwordHash, id);
    }

    public Long create(String username, String passwordHash, String nickname,
                       String role, boolean mustChangePassword) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_user
                        (username, password_hash, nickname, role, status,
                         must_change_password, token_version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, nickname);
            statement.setString(4, role);
            statement.setBoolean(5, mustChangePassword);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建账号后未返回用户 ID");
        }
        return key.longValue();
    }
}
