package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.operations.admin.dto.AdminSeriesDtos;
import com.wherelee.edu.modules.platform.aop.AdminAudit;
import com.wherelee.edu.modules.platform.aop.Idempotent;
import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import com.wherelee.edu.modules.platform.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content/series")
public class AdminSeriesController {

    private final AdminSeriesService service;

    public AdminSeriesController(AdminSeriesService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole("ADMIN")
    @Measured("admin.content.series.page")
    public PageResult<AdminSeriesVO> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long teacherId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.page(keyword, status, teacherId, page, size);
    }

    @GetMapping("/{id}")
    @RequireRole("ADMIN")
    @Measured("admin.content.series.detail")
    public AdminSeriesDtos.SeriesDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PutMapping("/{id}/status")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:content:seriesStatus", windowSeconds = 3)
    @Measured("admin.content.series.status")
    @AdminAudit(action = "ADMIN_SERIES_STATUS", targetType = "SERIES", targetIdArg = 1)
    public void updateStatus(@RequestAttribute("userId") Long adminId,
                             @PathVariable Long id,
                             @Valid @RequestBody AdminSeriesDtos.StatusRequest request) {
        service.updateStatus(id, request.status());
    }

    @PutMapping("/{id}/sale")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:content:seriesSale", windowSeconds = 3)
    @Measured("admin.content.series.sale")
    @AdminAudit(action = "ADMIN_SERIES_SALE", targetType = "SERIES", targetIdArg = 1)
    public void updateSale(@PathVariable Long id,
                           @Valid @RequestBody AdminSeriesDtos.SaleRequest request) {
        service.updateSale(id, request.saleStatus(), request.priceCents());
    }
}
