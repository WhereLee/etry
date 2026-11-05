package com.wherelee.edu.modules.commerce.order;

import com.wherelee.edu.modules.commerce.api.AdminOrderOperations;
import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.OrderFulfillmentService;
import com.wherelee.edu.modules.commerce.payment.PaymentDtos;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.commerce.payment.PaymentService;
import com.wherelee.edu.modules.commerce.payment.RefundRecord;
import com.wherelee.edu.modules.commerce.payment.RefundService;
import com.wherelee.edu.modules.platform.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * commerce 模块内部实现运营端订单门面。运营端不直接接触具体 Repository 和补偿服务。
 */
@Service
public class AdminOrderOperationsService implements AdminOrderOperations {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final OrderFulfillmentService fulfillmentService;
    private final RefundService refundService;

    public AdminOrderOperationsService(OrderService orderService,
                                       OrderRepository orderRepository,
                                       PaymentRepository paymentRepository,
                                       PaymentService paymentService,
                                       OrderFulfillmentService fulfillmentService,
                                       RefundService refundService) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.fulfillmentService = fulfillmentService;
        this.refundService = refundService;
    }

    @Override
    public Snapshot snapshot(String orderNo) {
        OrderDtos.OrderDetail detail = orderService.detailForAdmin(orderNo);
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
        return new Snapshot(detail, attempts, events, refund);
    }

    @Override
    public void queryNow(String orderNo) {
        paymentService.queryNow(orderNo);
    }

    @Override
    public void fulfill(String orderNo) {
        if (!fulfillmentService.fulfill(orderNo)) {
            throw new BizException(HttpStatus.CONFLICT, "订单暂未完成履约，请查看最近错误后重试");
        }
    }

    @Override
    public void close(String orderNo) {
        OrderRecord order = orderService.require(orderNo);
        OrderTransitionPolicy.requireAdminClosable(order.status());
        orderRepository.close(orderNo, true);
    }

    @Override
    public void refund(String orderNo, Long operatorId, String reason, boolean simulateFailure) {
        refundService.refund(orderNo, operatorId, reason, simulateFailure);
    }

    private PaymentDtos.RefundView refundView(RefundRecord refund) {
        return new PaymentDtos.RefundView(
                refund.refundNo(), refund.amountCents(), refund.status(),
                refund.attemptCount(), refund.nextRetryAt(), refund.reason(),
                refund.requestedAt(), refund.succeededAt(), refund.errorMessage());
    }
}