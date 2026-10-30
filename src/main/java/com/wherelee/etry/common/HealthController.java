package com.wherelee.etry.common;

import com.wherelee.etry.cache.KeyValueStore;
import com.wherelee.etry.storage.StorageGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：数据库、键值存储、对象存储三项都通了才算健康。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private static final String PROBE_KEY = "etry:health:probe";
    private static final String PROBE_OBJECT = "__health_probe__";

    private final DataSource dataSource;
    private final KeyValueStore keyValueStore;
    private final StorageGateway storageGateway;

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("db", checkDb());
        checks.put("cache", checkCache());
        checks.put("storage", checkStorage());

        boolean healthy = checks.values().stream().allMatch("ok"::equals);
        if (healthy) {
            return ResponseEntity.ok(ApiResponse.ok(checks));
        }
        return ResponseEntity.status(503)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "依赖项异常：" + checks));
    }

    private String checkDb() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "ok" : "down";
        } catch (Exception e) {
            return "down";
        }
    }

    private String checkCache() {
        try {
            String value = String.valueOf(System.nanoTime());
            keyValueStore.setEx(PROBE_KEY, value, Duration.ofSeconds(5));
            boolean match = value.equals(keyValueStore.get(PROBE_KEY));
            keyValueStore.delete(PROBE_KEY);
            return match ? "ok" : "down";
        } catch (Exception e) {
            return "down";
        }
    }

    private String checkStorage() {
        try {
            storageGateway.stat(PROBE_OBJECT);
            return "ok";
        } catch (Exception e) {
            return "down";
        }
    }
}
