package com.wherelee.edu.admin;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.order.OrderRecord;
import com.wherelee.edu.order.OrderRepository;
import com.wherelee.edu.order.OrderService;
import com.wherelee.edu.order.OrderStatus;
import com.wherelee.edu.payment.OrderFulfillmentService;
import com.wherelee.edu.payment.PaymentDtos;
import com.wherelee.edu.payment.PaymentRepository;
import com.wherelee.edu.payment.PaymentService;
import com.wherelee.edu.payment.RefundRecord;
import com.wherelee.edu.payment.RefundService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminOrderMapper mapper;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final OrderFulfillmentService fulfillmentService;
    private final RefundService refundService;

    public AdminOrderService(AdminOrderMapper mapper,
                             OrderService orderService,
                             OrderRepository orderRepository,
                             PaymentRepository paymentRepository,
                             PaymentService paymentService,
                             OrderFulfillmentService fulfillmentService,
                             RefundService refundService) {
        this.mapper = mapper;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.fulfillmentService = fulfillmentService;
        this.refundService = refundService;
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
        OrderRecord order = orderService.require(orderNo);
        List<PaymentDtos.AttemptView> attempts = paymentRepository.findAttempts(order.id()).stream()
                .map(attempt -> new PaymentDtos.AttemptView(
                        attempt.attemptNo(), attempt.status(), attempt.providerTradeNo(),
                        attempt.providerStatus(), attempt.mockScenario(),
                        attempt.createdAt(), attempt.finishedAt()))
                .toList();
        List<PaymentDtos.EventView> events = paymentRepository.findEvents(order.id()).stream()
                .map(event -> new PaymentDtos.EventView(
                        event.eventKey(), event.eventType(), event.amountCents(),
                        event.processStatus(), event.errorMessage(),
                        event.receivedAt(), event.processedAt()))
                .toList();
        PaymentDtos.RefundView refund = paymentRepository.findRefundByOrder(order.id())
                .map(this::refundView).orElse(null);
        return new AdminOrderDtos.Detail(row, orderService.detailForAdmin(orderNo), attempts, events, refund);
    }

    public AdminOrderDtos.Detail query(String orderNo) {
        paymentService.queryNow(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail fulfill(String orderNo) {
        if (!fulfillmentService.fulfill(orderNo)) {
            throw new BizException(org.springframework.http.HttpStatus.CONFLICT,
                    "订单暂未完成履约，请查看最近错误后重试");
        }
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail close(String orderNo) {
        OrderRecord order = orderService.require(orderNo);
        if (order.status() == OrderStatus.PAID || order.status() == OrderStatus.FULFILLED
                || order.status() == OrderStatus.REFUNDING || order.status() == OrderStatus.REFUNDED) {
            throw BizException.badRequest("已支付订单不能直接关闭");
        }
        if (order.status() == OrderStatus.PAYING) {
            throw BizException.badRequest("支付中订单不能直接关闭，请先主动查单");
        }
        orderRepository.close(orderNo, true);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail refund(String orderNo, Long operatorId,
                                        PaymentDtos.RefundRequest request) {
        refundService.refund(orderNo, operatorId, request.reason(), request.simulateFailure());
        return detail(orderNo);
    }

    private PaymentDtos.RefundView refundView(RefundRecord refund) {
        return new PaymentDtos.RefundView(
                refund.refundNo(), refund.amountCents(), refund.status(), refund.reason(),
                refund.requestedAt(), refund.succeededAt(), refund.errorMessage());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
