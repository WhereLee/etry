package com.wherelee.edu.admin;

import com.wherelee.edu.aop.AdminAudit;
import com.wherelee.edu.aop.Idempotent;
import com.wherelee.edu.aop.Measured;
import com.wherelee.edu.aop.RateLimit;
import com.wherelee.edu.aop.RequireRole;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.payment.PaymentDtos;
import jakarta.validation.Valid;
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

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole("ADMIN")
    @Measured("admin.order.page")
    public PageResult<AdminOrderVO> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.page(keyword, status, page, size);
    }

    @GetMapping("/exceptions")
    @RequireRole("ADMIN")
    @Measured("admin.order.exceptions")
    public PageResult<AdminOrderVO> exceptions(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.exceptions(page, size);
    }

    @GetMapping("/{orderNo}")
    @RequireRole("ADMIN")
    @Measured("admin.order.detail")
    public AdminOrderDtos.Detail detail(@PathVariable String orderNo) {
        return service.detail(orderNo);
    }

    @PostMapping("/{orderNo}/query")
    @RequireRole("ADMIN")
    @RateLimit(key = "admin:orderQuery", limit = 30, windowSeconds = 60)
    @Measured("admin.order.query")
    @AdminAudit(action = "ORDER_QUERY", targetType = "ORDER")
    public AdminOrderDtos.Detail query(@PathVariable String orderNo) {
        return service.query(orderNo);
    }

    @PostMapping("/{orderNo}/fulfill")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:orderFulfill", windowSeconds = 3)
    @Measured("admin.order.fulfill")
    @AdminAudit(action = "ORDER_FULFILL", targetType = "ORDER")
    public AdminOrderDtos.Detail fulfill(@PathVariable String orderNo) {
        return service.fulfill(orderNo);
    }

    @PostMapping("/{orderNo}/close")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:orderClose", windowSeconds = 3)
    @Measured("admin.order.close")
    @AdminAudit(action = "ORDER_CLOSE", targetType = "ORDER")
    public AdminOrderDtos.Detail close(@PathVariable String orderNo) {
        return service.close(orderNo);
    }

    @PostMapping("/{orderNo}/refund")
    @RequireRole("ADMIN")
    @Idempotent(key = "admin:orderRefund", windowSeconds = 5)
    @Measured("admin.order.refund")
    @AdminAudit(action = "ORDER_REFUND", targetType = "ORDER")
    public AdminOrderDtos.Detail refund(@RequestAttribute("userId") Long operatorId,
                                        @PathVariable String orderNo,
                                        @Valid @RequestBody PaymentDtos.RefundRequest request) {
        return service.refund(orderNo, operatorId, request);
    }
}
