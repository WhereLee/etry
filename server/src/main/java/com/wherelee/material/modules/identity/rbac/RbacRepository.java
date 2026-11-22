package com.wherelee.material.modules.identity.rbac;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RbacRepository {

    private static final RowMapper<RoleRecord> ROLE_MAPPER = (rs, rowNum) -> new RoleRecord(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("data_scope"),
            rs.getBoolean("built_in"),
            rs.getString("remark"),
            toLocal(rs.getTimestamp("created_at"))
    );

    private final JdbcTemplate jdbc;

    public RbacRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoleRecord> listRoles() {
        return jdbc.query("SELECT * FROM t_role ORDER BY id", ROLE_MAPPER);
    }

    public Optional<RoleRecord> findRoleByCode(String code) {
        return jdbc.query("SELECT * FROM t_role WHERE code = ?", ROLE_MAPPER, code)
                .stream().findFirst();
    }

    public List<String> findRoleCodesByUser(long userId) {
        return jdbc.queryForList("""
                SELECT r.code FROM t_user_role ur
                JOIN t_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? ORDER BY r.id
                """, String.class, userId);
    }

    public List<String> findPermissionCodesByUser(long userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.code FROM t_user_role ur
                JOIN t_role_permission rp ON rp.role_id = ur.role_id
                JOIN t_permission p ON p.id = rp.permission_id
                WHERE ur.user_id = ? ORDER BY p.code
                """, String.class, userId);
    }

    /** 幂等授角色；角色不存在返回 false。 */
    public boolean grantRole(long userId, String roleCode, Long grantedBy) {
        return jdbc.update("""
                INSERT IGNORE INTO t_user_role (user_id, role_id, granted_by)
                SELECT ?, id, ? FROM t_role WHERE code = ?
                """, userId, grantedBy, roleCode) > 0;
    }

    public int revokeRole(long userId, String roleCode) {
        return jdbc.update("""
                DELETE ur FROM t_user_role ur
                JOIN t_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.code = ?
                """, userId, roleCode);
    }

    public void upsertPermission(String code, String name, String module) {
        jdbc.update("""
                INSERT INTO t_permission (code, name, module) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), module = VALUES(module)
                """, code, name, module);
    }

    /** 幂等绑定角色-权限点；任一不存在返回 false。 */
    public boolean linkRolePermission(String roleCode, String permissionCode) {
        return jdbc.update("""
                INSERT IGNORE INTO t_role_permission (role_id, permission_id)
                SELECT r.id, p.id FROM t_role r, t_permission p
                WHERE r.code = ? AND p.code = ?
                """, roleCode, permissionCode) > 0;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}