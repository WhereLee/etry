package com.wherelee.material.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据层归属守卫：补 ArchUnit 只守 Java import 的盲点。
 *
 * <p>每张表唯一归属一个模块；扫描 JdbcTemplate SQL 常量与 MyBatis XML 的表名，
 * 跨归属访问即失败。跨表 join 只允许出现在显式登记的读模型门面（READ_MODEL_EXEMPTIONS）。
 * 新增表须在 OWNER 登记归属，新增跨读须在读模型豁免中显式登记，否则本测试拦截。</p>
 */
class TableOwnershipTest {

    private static final Map<String, String> OWNER = Map.ofEntries(
            Map.entry("t_user", "identity"),
            Map.entry("t_teacher_invitation", "identity"),
            Map.entry("t_role", "identity"),
            Map.entry("t_permission", "identity"),
            Map.entry("t_role_permission", "identity"),
            Map.entry("t_user_role", "identity"),
            Map.entry("t_material", "material"),
            Map.entry("t_series", "course"),
            Map.entry("t_series_chapter", "course"),
            Map.entry("t_series_video", "course"),
            Map.entry("t_learning_progress", "learning"),
            Map.entry("t_playback_log", "learning"),
            Map.entry("t_order", "commerce"),
            Map.entry("t_order_item", "commerce"),
            Map.entry("t_cart_item", "commerce"),
            Map.entry("t_payment_attempt", "commerce"),
            Map.entry("t_payment_event", "commerce"),
            Map.entry("t_refund", "commerce"),
            Map.entry("t_outbox_message", "commerce"),
            Map.entry("t_recon_statement_file", "commerce"),
            Map.entry("t_recon_statement_item", "commerce"),
            Map.entry("t_recon_run", "commerce"),
            Map.entry("t_recon_match", "commerce"),
            Map.entry("t_recon_break", "commerce"),
            Map.entry("t_recon_action", "commerce"),
            Map.entry("t_entitlement", "entitlement"),
            Map.entry("t_student_series", "entitlement"),
            Map.entry("t_alert_log", "platform"),
            Map.entry("t_admin_audit_log", "platform")
    );

    /** 读模型门面豁免：accessor 简名 → 允许跨读的表。跨表 join 只留于此。 */
    private static final Map<String, Set<String>> READ_MODEL_EXEMPTIONS = Map.of(
            "SeriesRepository", Set.of("t_user", "t_learning_progress"),
            "AdminSeriesMapper", Set.of("t_user", "t_entitlement"),
            "AdminOrderMapper", Set.of("t_order", "t_user")
    );

    private static final Pattern TABLE =
            Pattern.compile("(?i)\\b(?:FROM|JOIN|INTO|UPDATE)\\s+(t_[a-z_]+)");
    private static final Pattern NAMESPACE =
            Pattern.compile("namespace=\"com\\.wherelee\\.material\\.modules\\.(\\w+)\\.");
    private static final String MODULE_PREFIX = "com/wherelee/material/modules/";

    @Test
    void sqlTableAccessRespectsOwnership() throws IOException {
        List<String> violations = new ArrayList<>();
        Path javaRoot = sourceRoot("src/main/java");
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = javaRoot.relativize(path).toString().replace('\\', '/');
                if (!rel.startsWith(MODULE_PREFIX)) {
                    continue;
                }
                String module = rel.substring(MODULE_PREFIX.length()).split("/")[0];
                String accessor = path.getFileName().toString().replace(".java", "");
                check(module, accessor, Files.readString(path), violations);
            }
        }
        Path mapperRoot = sourceRoot("src/main/resources/mapper");
        try (Stream<Path> paths = Files.walk(mapperRoot)) {
            for (Path path : paths.filter(f -> f.toString().endsWith(".xml")).toList()) {
                String content = Files.readString(path);
                Matcher ns = NAMESPACE.matcher(content);
                String module = ns.find() ? ns.group(1) : "unknown";
                String accessor = path.getFileName().toString().replace(".xml", "");
                check(module, accessor, content, violations);
            }
        }
        assertThat(violations)
                .as("跨归属表访问须在 OWNER 登记归属或在 READ_MODEL_EXEMPTIONS 显式豁免")
                .isEmpty();
    }

    private void check(String module, String accessor, String content, List<String> violations) {
        Matcher matcher = TABLE.matcher(content);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase();
            if (!seen.add(table)) {
                continue;
            }
            String owner = OWNER.get(table);
            if (owner == null) {
                violations.add(accessor + " 访问未登记归属的表 " + table);
                continue;
            }
            if (owner.equals(module)) {
                continue;
            }
            if (!READ_MODEL_EXEMPTIONS.getOrDefault(accessor, Set.of()).contains(table)) {
                violations.add(module + "." + accessor + " 跨归属访问 " + table + "（归属 " + owner + "）");
            }
        }
    }

    private Path sourceRoot(String relative) {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path nested = Path.of("server", relative);
        if (Files.exists(nested)) {
            return nested;
        }
        throw new IllegalStateException("找不到源码目录：" + relative);
    }
}