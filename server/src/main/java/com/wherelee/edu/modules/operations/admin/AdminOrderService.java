package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.commerce.api.AdminOrderOperations;
import com.wherelee.edu.modules.commerce.payment.PaymentDtos;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.common.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminOrderMapper mapper;
    private final AdminOrderOperations orderOperations;

    public AdminOrderService(AdminOrderMapper mapper,
                             AdminOrderOperations orderOperations) {
        this.mapper = mapper;
        this.orderOperations = orderOperations;
    }

    public PageResult<AdminOrderVO> page(String keyword, String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = mapper.count(blankToNull(keyword), blankToNull(status));
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        return new PageResult<>(mapper.findPage(blankToNull(keyword), blankToNull(status),
                (safePage - 1) * safeSize, safeSize), total, safePage, safeSize);
    }

    public PageResult<AdminOrderVO> exceptions(int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = mapper.countExceptions();
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        return new PageResult<>(mapper.findExceptions((safePage - 1) * safeSize, safeSize),
                total, safePage, safeSize);
    }

    public AdminOrderDtos.Detail detail(String orderNo) {
        AdminOrderVO row = mapper.findOne(orderNo);
        if (row == null) {
            throw BizException.notFound("订单不存在");
        }
        AdminOrderOperations.Snapshot snapshot = orderOperations.snapshot(orderNo);
        return new AdminOrderDtos.Detail(
                row,
                snapshot.orderDetail(),
                snapshot.attempts(),
                snapshot.events(),
                snapshot.refund()
        );
    }

    public AdminOrderDtos.Detail query(String orderNo) {
        orderOperations.queryNow(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail fulfill(String orderNo) {
        orderOperations.fulfill(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail close(String orderNo) {
        orderOperations.close(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail refund(String orderNo, Long operatorId,
                                        PaymentDtos.RefundRequest request) {
        orderOperations.refund(orderNo, operatorId, request.reason(), request.simulateFailure());
        return detail(orderNo);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}