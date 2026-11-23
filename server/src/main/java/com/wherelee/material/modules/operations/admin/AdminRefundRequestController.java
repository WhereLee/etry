package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.commerce.api.AdminOrderOperations;
import com.wherelee.material.modules.commerce.api.RefundRequestDtos;
import com.wherelee.material.modules.platform.aop.AdminAudit;
import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.common.PageResult;
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

/**
 * 客服/财务处理买家退款申请（PRD §2 客服「处理退款申请」）。同意即走既有整单退款内核。
 */
@RestController
@RequestMapping("/api/admin/refund-requests")
public class AdminRefundRequestController {

    private final AdminOrderOperations operations;

    public AdminRefundRequestController(AdminOrderOperations operations) {
        this.operations = operations;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('refund:operate')")
    @Measured("admin.refundRequest.page")
    public PageResult<RefundRequestDtos.AdminView> page(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return operations.refundRequests(status, page, size);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('refund:operate')")
    @Measured("admin.refundRequest.approve")
    @AdminAudit(action = "REFUND_REQUEST_APPROVE", targetType = "REFUND_REQUEST", targetIdArg = 1)
    public void approve(@RequestAttribute("userId") Long operatorId, @PathVariable Long id) {
        operations.approveRefundRequest(id, operatorId);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('refund:operate')")
    @Measured("admin.refundRequest.reject")
    @AdminAudit(action = "REFUND_REQUEST_REJECT", targetType = "REFUND_REQUEST", targetIdArg = 1)
    public void reject(@RequestAttribute("userId") Long operatorId,
                       @PathVariable Long id,
                       @Valid @RequestBody RefundRequestDtos.RejectRequest request) {
        operations.rejectRefundRequest(id, operatorId, request.note());
    }
}