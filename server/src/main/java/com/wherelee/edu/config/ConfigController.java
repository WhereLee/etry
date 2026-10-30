package com.wherelee.edu.config;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.config.dto.ConfigVO;
import com.wherelee.edu.config.dto.UpdateConfigRequest;
import com.wherelee.edu.rbac.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    @RequirePermission("config:manage")
    public ApiResponse<List<ConfigVO>> list() {
        return ApiResponse.ok(configService.list());
    }

    @PutMapping("/{key}")
    @RequirePermission("config:manage")
    @AuditLog(action = "CONFIG_UPDATE", targetType = "CONFIG", targetIdIndex = -1)
    public ApiResponse<Void> update(@PathVariable String key,
                                    @Valid @RequestBody UpdateConfigRequest request) {
        configService.update(key, request.value());
        return ApiResponse.ok();
    }
}
