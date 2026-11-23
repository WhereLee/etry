package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.platform.aop.AdminAudit;
import com.wherelee.material.modules.platform.aop.Idempotent;
import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.aop.RateLimit;
import com.wherelee.material.modules.platform.common.PageResult;
import com.wherelee.material.modules.commerce.api.PaymentDtos;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;
    private final AdminExportService exportService;

    public AdminOrderController(AdminOrderService service, AdminExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('order:read')")
    @Measured("admin.order.export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       HttpServletResponse response) throws java.io.IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=orders-export.xlsx");
        exportService.exportOrders(response.getOutputStream(), keyword, status);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    @Measured("admin.order.page")
    public PageResult<AdminOrderVO> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.page(keyword, status, page, size);
    }

    @GetMapping("/exceptions")
    @PreAuthorize("hasAuthority('order:read')")
    @Measured("admin.order.exceptions")
    public PageResult<AdminOrderVO> exceptions(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.exceptions(page, size);
    }

    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAuthority('order:read')")
    @Measured("admin.order.detail")
    public AdminOrderDtos.Detail detail(@PathVariable String orderNo) {
        return service.detail(orderNo);
    }

    @PostMapping("/{orderNo}/query")
    @PreAuthorize("hasAuthority('order:operate')")
    @RateLimit(key = "admin:orderQuery", limit = 30, windowSeconds = 60)
    @Measured("admin.order.query")
    @AdminAudit(action = "ORDER_QUERY", targetType = "ORDER")
    public AdminOrderDtos.Detail query(@PathVariable String orderNo) {
        return service.query(orderNo);
    }

    @PostMapping("/{orderNo}/fulfill")
    @PreAuthorize("hasAuthority('order:operate')")
    @Idempotent(key = "admin:orderFulfill", windowSeconds = 3)
    @Measured("admin.order.fulfill")
    @AdminAudit(action = "ORDER_FULFILL", targetType = "ORDER")
    public AdminOrderDtos.Detail fulfill(@PathVariable String orderNo) {
        return service.fulfill(orderNo);
    }

    @PostMapping("/{orderNo}/close")
    @PreAuthorize("hasAuthority('order:operate')")
    @Idempotent(key = "admin:orderClose", windowSeconds = 3)
    @Measured("admin.order.close")
    @AdminAudit(action = "ORDER_CLOSE", targetType = "ORDER")
    public AdminOrderDtos.Detail close(@PathVariable String orderNo) {
        return service.close(orderNo);
    }

    @PostMapping("/{orderNo}/refund")
    @PreAuthorize("hasAuthority('refund:operate')")
    @Idempotent(key = "admin:orderRefund", windowSeconds = 5)
    @Measured("admin.order.refund")
    @AdminAudit(action = "ORDER_REFUND", targetType = "ORDER")
    public AdminOrderDtos.Detail refund(@RequestAttribute("userId") Long operatorId,
                                        @PathVariable String orderNo,
                                        @Valid @RequestBody PaymentDtos.RefundRequest request) {
        return service.refund(orderNo, operatorId, request);
    }

    @PostMapping("/{orderNo}/attempts/{attemptNo}/refund")
    @PreAuthorize("hasAuthority('refund:operate')")
    @Idempotent(key = "admin:duplicateRefund", windowSeconds = 5)
    @Measured("admin.order.refund.duplicate")
    @AdminAudit(action = "ORDER_DUPLICATE_REFUND", targetType = "ORDER")
    public AdminOrderDtos.Detail refundDuplicate(@RequestAttribute("userId") Long operatorId,
                                                 @PathVariable String orderNo,
                                                 @PathVariable int attemptNo,
                                                 @Valid @RequestBody PaymentDtos.RefundRequest request) {
        return service.refundDuplicate(orderNo, attemptNo, operatorId, request);
    }
}
