package com.wherelee.material.modules.platform.audit;

import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.audit.AdminAuditRepository.AuditLogRecord;
import com.wherelee.material.modules.platform.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 超级管理员审计日志查询（PRD §2 超级管理员「审计日志」、§4）。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private final AdminAuditRepository repository;

    public AdminAuditLogController(AdminAuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Measured("admin.audit.page")
    public PageResult<AuditLogRecord> page(@RequestParam(required = false) Long operatorId,
                                           @RequestParam(required = false) String action,
                                           @RequestParam(required = false) String targetType,
                                           @RequestParam(required = false) Boolean success,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        long total = repository.count(operatorId, action, targetType, success);
        List<AuditLogRecord> records =
                repository.page(operatorId, action, targetType, success, safeSize, (long) (safePage - 1) * safeSize);
        return new PageResult<>(records, total, safePage, safeSize);
    }
}