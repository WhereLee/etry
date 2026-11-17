package com.wherelee.edu.modules.identity.user;

import com.wherelee.edu.modules.identity.api.UserDirectoryQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户目录读适配器：只读 identity 自己归属的 t_user。
 */
@Component
public class UserDirectoryQueryAdapter implements UserDirectoryQuery {

    private final JdbcTemplate jdbc;

    public UserDirectoryQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, String> nicknamesById(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, String> nicknames = new HashMap<>();
        RowCallbackHandler handler = rs -> nicknames.put(rs.getLong("id"), rs.getString("nickname"));
        jdbc.query("SELECT id, nickname FROM t_user WHERE id IN (" + placeholders + ")",
                handler, ids.toArray());
        return nicknames;
    }
}