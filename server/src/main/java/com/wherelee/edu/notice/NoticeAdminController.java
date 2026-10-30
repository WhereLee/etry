package com.wherelee.edu.notice;

import com.wherelee.edu.audit.AuditLog;
import com.wherelee.edu.common.ApiResponse;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.notice.dto.NoticeDtos;
import com.wherelee.edu.rbac.RequirePermission;
import com.wherelee.edu.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class NoticeAdminController {

    private final NoticeService noticeService;

    @GetMapping
    @RequirePermission("notice:manage")
    public ApiResponse<PageResult<NoticeDtos.NoticeSummaryVO>> page(@RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(noticeService.adminPage(status, page, size));
    }

    @PostMapping
    @RequirePermission("notice:manage")
    @AuditLog(action = "NOTICE_CREATE", targetType = "NOTICE")
    public ApiResponse<NoticeDtos.NoticeVO> create(@CurrentUser Long publisherId,
                                                   @Valid @RequestBody NoticeDtos.SaveRequest request) {
        return ApiResponse.ok(noticeService.create(publisherId, request));
    }

    @PutMapping("/{id}")
    @RequirePermission("notice:manage")
    @AuditLog(action = "NOTICE_UPDATE", targetType = "NOTICE", targetIdIndex = 0)
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody NoticeDtos.SaveRequest request) {
        noticeService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("notice:manage")
    @AuditLog(action = "NOTICE_DELETE", targetType = "NOTICE", targetIdIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return ApiResponse.ok();
    }
}
