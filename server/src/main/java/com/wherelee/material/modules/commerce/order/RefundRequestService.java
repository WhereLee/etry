package com.wherelee.material.modules.commerce.order;

import com.wherelee.material.modules.commerce.api.RefundRequestDtos;
import com.wherelee.material.modules.commerce.payment.RefundService;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 买家整单退款申请：买家提交申请，客服/财务同意后走既有退款内核，拒绝留痕。
 * 不改变订单状态机与资金语义，申请处理结果复用 RefundService。
 */
@Service
public class RefundRequestService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> STATUSES = Set.of(
            RefundRequestRecord.PENDING, RefundRequestRecord.APPROVED, RefundRequestRecord.REJECTED);

    private final RefundRequestRepository repository;
    private final OrderService orderService;
    private final RefundService refundService;

    public RefundRequestService(RefundRequestRepository repository,
                                OrderService orderService,
                                RefundService refundService) {
        this.repository = repository;
        this.orderService = orderService;
        this.refundService = refundService;
    }

    public RefundRequestDtos.BuyerView request(Long userId, String orderNo, String reason) {
        OrderRecord order = orderService.requireOwned(userId, orderNo);
        OrderTransitionPolicy.requireRefundable(order.status());
        Optional<RefundRequestRecord> existing = repository.findByOrderId(order.id());
        if (existing.isPresent()) {
            RefundRequestRecord current = existing.get();
            if (RefundRequestRecord.PENDING.equals(current.status())) {
                throw BizException.badRequest("退款申请正在处理中，请耐心等待");
            }
            if (RefundRequestRecord.APPROVED.equals(current.status())) {
                throw BizException.badRequest("该订单的退款申请已同意");
            }
            if (repository.reapply(order.id(), userId, reason) == 0) {
                throw new BizException(HttpStatus.CONFLICT, "退款申请状态已变化，请刷新后重试");
            }
        } else {
            try {
                repository.insert(order.id(), orderNo, userId, reason);
            } catch (DuplicateKeyException e) {
                throw BizException.badRequest("退款申请正在处理中，请耐心等待");
            }
        }
        return repository.findByOrderId(order.id()).map(this::buyerView)
                .orElseThrow(() -> new IllegalStateException("退款申请创建后不可读取"));
    }

    public RefundRequestDtos.BuyerView latest(Long userId, String orderNo) {
        OrderRecord order = orderService.requireOwned(userId, orderNo);
        return repository.findByOrderId(order.id()).map(this::buyerView).orElse(null);
    }

    public PageResult<RefundRequestDtos.AdminView> adminPage(String status, int page, int size) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status.strip())) {
            throw BizException.badRequest("非法申请状态");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.min(Math.max(page, 1), 10_000);
        long total = repository.adminCount(status);
        List<RefundRequestDtos.AdminView> records = repository
                .adminPage(status, safeSize, (long) (safePage - 1) * safeSize)
                .stream().map(this::adminView).toList();
        return new PageResult<>(records, total, safePage, safeSize);
    }

    public void approve(long requestId, Long operatorId) {
        RefundRequestRecord request = require(requestId);
        requirePending(request);
        OrderRecord order = orderService.require(request.orderNo());
        OrderTransitionPolicy.requireRefundable(order.status());
        if (repository.markHandled(requestId, RefundRequestRecord.APPROVED, operatorId, null) == 0) {
            throw new BizException(HttpStatus.CONFLICT, "退款申请已处理");
        }
        try {
            refundService.refund(request.orderNo(), operatorId, "买家申请退款：" + request.reason(), false);
        } catch (RuntimeException e) {
            repository.revertToPending(requestId, operatorId);
            throw e;
        }
    }

    public void reject(long requestId, Long operatorId, String note) {
        RefundRequestRecord request = require(requestId);
        requirePending(request);
        if (repository.markHandled(requestId, RefundRequestRecord.REJECTED, operatorId, note) == 0) {
            throw new BizException(HttpStatus.CONFLICT, "退款申请已处理");
        }
    }

    private RefundRequestRecord require(long requestId) {
        return repository.findById(requestId)
                .orElseThrow(() -> BizException.notFound("退款申请不存在"));
    }

    private void requirePending(RefundRequestRecord request) {
        if (!RefundRequestRecord.PENDING.equals(request.status())) {
            throw new BizException(HttpStatus.CONFLICT, "退款申请已处理");
        }
    }

    private RefundRequestDtos.BuyerView buyerView(RefundRequestRecord record) {
        return new RefundRequestDtos.BuyerView(record.id(), record.orderNo(), record.reason(),
                record.status(), record.createdAt(), record.handledAt(), record.handleNote());
    }

    private RefundRequestDtos.AdminView adminView(RefundRequestRepository.AdminRow row) {
        RefundRequestRecord record = row.request();
        return new RefundRequestDtos.AdminView(record.id(), record.orderNo(), row.username(), row.nickname(),
                row.orderStatus(), row.priceCents(), record.reason(), record.status(),
                record.createdAt(), record.handledBy(), record.handledAt(), record.handleNote());
    }
}