package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.operations.admin.dto.AdminUserVO;
import com.wherelee.edu.modules.operations.admin.dto.AssignSeriesRequest;
import com.wherelee.edu.modules.operations.admin.dto.ChangeStatusRequest;
import com.wherelee.edu.modules.operations.admin.dto.CreateUserRequest;
import com.wherelee.edu.modules.operations.admin.dto.ResetPasswordRequest;
import com.wherelee.edu.modules.operations.admin.dto.SeriesOptionVO;
import com.wherelee.edu.modules.platform.aop.AdminAudit;
import com.wherelee.edu.modules.platform.aop.Idempotent;
import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RateLimit;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import com.wherelee.edu.modules.platform.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @RequireRole("ADMIN")
    @Measured("admin.user.page")
    public PageResult<AdminUserVO> users(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return service.page(keyword, role, status, page, size);
    }

    @PostMapping("/users")
    @RequireRole("ADMIN")
    @RateLimit(key = "admin:createUser", limit = 30, windowSeconds = 60)
    @Idempotent(key = "admin:createUser", windowSeconds = 5)
    @Measured("admin.user.create")
    @AdminAudit(action = "USER_CREATE", targetType = "USER", targetIdFromResult = true)
    public Long create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @PutMapping("/users/{id}/status")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:changeUserStatus", windowSeconds = 3)
    @Measured("admin.user.status")
    @AdminAudit(action = "USER_STATUS_CHANGE", targetType = "USER", targetIdArg = 1)
    public void changeStatus(@RequestAttribute("userId") Long operatorId,
                             @PathVariable Long id,
                             @Valid @RequestBody ChangeStatusRequest request) {
        service.changeStatus(operatorId, id, request.status());
    }

    @PutMapping("/users/{id}/password")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:resetUserPassword", windowSeconds = 5)
    @Measured("admin.user.resetPassword")
    @AdminAudit(action = "USER_PASSWORD_RESET", targetType = "USER", targetIdArg = 1)
    public void resetPassword(@RequestAttribute("userId") Long operatorId,
                              @PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(operatorId, id, request.newPassword());
    }

    @GetMapping("/series")
    @RequireRole("ADMIN")
    @Measured("admin.series.options")
    public List<SeriesOptionVO> series() {
        return service.seriesOptions();
    }

    @GetMapping("/users/{id}/series")
    @RequireRole("ADMIN")
    @Measured("admin.user.series")
    public List<Long> userSeries(@PathVariable Long id) {
        return service.seriesIds(id);
    }

    @PutMapping("/users/{id}/series")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:assignSeries", windowSeconds = 3)
    @Measured("admin.user.assignSeries")
    @AdminAudit(action = "USER_SERIES_ASSIGN", targetType = "USER", targetIdArg = 0)
    public void assignSeries(@PathVariable Long id, @RequestBody AssignSeriesRequest request) {
        service.assignSeries(id, request);
    }
}
