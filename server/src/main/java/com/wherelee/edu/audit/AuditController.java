package com.wherelee.edu.audit;

import com.wherelee.edu.audit.dto.AuditLogVO;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.rbac.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @RequirePermission("user:manage")
    public ApiResponse<PageResult<AuditLogVO>> page(@RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        PageResult<AuditRecord> raw = auditService.page(userId, action, page, size);
        return ApiResponse.ok(PageResult.of(
                raw.records().stream().map(AuditLogVO::from).toList(),
                raw.total(), raw.page(), raw.size()));
    }
}
