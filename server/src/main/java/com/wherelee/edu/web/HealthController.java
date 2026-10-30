package com.wherelee.edu.web;

import com.wherelee.edu.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存活探针。不查数据库、不查 Redis，只证明进程还活着，
 * 免得数据库抖一下就把整个实例判死。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("app", "edu");
        body.put("time", LocalDateTime.now().toString());
        return ApiResponse.ok(body);
    }
}
