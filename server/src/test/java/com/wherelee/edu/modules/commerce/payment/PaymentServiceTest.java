package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderService;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.tracing.TraceSpans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private com.wherelee.edu.modules.commerce.entitlement.EntitlementRepository entitlementRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentConfirmationService confirmationService;
    @Mock private OrderFulfillmentService fulfillmentService;
    @Mock private MockSignatureService signatureService;
    @Mock private PaymentMetrics metrics;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                orderService, orderRepository, paymentRepository, entitlementRepository,
                paymentGateway, confirmationService, fulfillmentService, signatureService,
                new PaymentProperties("alipay", false, null, 15, 60, 50, 60000, 30000, 5, 30),
                metrics, TraceSpans.noop());
    }

    @Test
    void expiredPayingOrderClosesWhenProviderReportsTradeNotFound() {
        OrderRecord order = order();
        PaymentAttemptRecord attempt = attempt();
        when(orderService.require("ORD-1")).thenReturn(order);
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt));
        when(paymentGateway.query(order, attempt)).thenReturn(
                new PaymentGateway.PaymentQueryResult("NOT_FOUND", order.priceCents(),
                        attempt.providerTradeNo(), "ACQ.TRADE_NOT_EXIST"));

        service.reconcileOrder(order);

        verify(orderRepository).closeAfterProviderNotFound("ORD-1");
    }

    @Test
    void payingOrderCannotReuseTerminalPaymentAttempt() {
        OrderRecord order = order();
        PaymentAttemptRecord attempt = new PaymentAttemptRecord(
                1L, 1L, 1, "ALIPAY", "FAILED", "ORD-1_1",
                "CREATED", 1L, "digest", "PROVIDER_FAILED", "failed", null,
                LocalDateTime.now(), LocalDateTime.now());
        when(orderService.requireOwned(1L, "ORD-1")).thenReturn(order);
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.pay(1L, "ORD-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("支付尝试已结束，请刷新订单状态");
    }
    @Test
    void reconcileClosesSupersededAttemptWhenGatewayConfirmsClosed() {
        stubEmptyReconcileQueues();
        PaymentAttemptRecord superseded = supersededAttempt();
        when(paymentRepository.findSupersededAttempts(anyInt())).thenReturn(List.of(superseded));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(paymentGateway.close(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaymentGateway.CloseResult("CLOSED", null));

        service.reconcile();

        verify(paymentRepository).markAttemptClosed(superseded.id());
    }

    @Test
    void reconcileClosesSupersededAttemptWhenTradeNotFound() {
        stubEmptyReconcileQueues();
        PaymentAttemptRecord superseded = supersededAttempt();
        when(paymentRepository.findSupersededAttempts(anyInt())).thenReturn(List.of(superseded));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(paymentGateway.close(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaymentGateway.CloseResult("NOT_FOUND", "ACQ.TRADE_NOT_EXIST"));

        service.reconcile();

        verify(paymentRepository).markAttemptClosed(superseded.id());
    }

    @Test
    void reconcileKeepsSupersededAttemptWhenClosePending() {
        stubEmptyReconcileQueues();
        PaymentAttemptRecord superseded = supersededAttempt();
        when(paymentRepository.findSupersededAttempts(anyInt())).thenReturn(List.of(superseded));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(paymentGateway.close(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaymentGateway.CloseResult("PENDING", "网络异常"));

        service.reconcile();

        verify(paymentRepository, never()).markAttemptClosed(org.mockito.ArgumentMatchers.anyLong());
    }

    private void stubEmptyReconcileQueues() {
        when(orderRepository.findPendingQuery(anyInt(), anyInt())).thenReturn(List.of());
        when(orderRepository.findExpired(anyInt())).thenReturn(List.of());
        when(orderRepository.findPaidUnfulfilled(anyInt())).thenReturn(List.of());
    }

    private PaymentAttemptRecord supersededAttempt() {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentAttemptRecord(1L, 1L, 1, "ALIPAY", "SUPERSEDED", "ORD-1_1",
                "CREATED", 1L, "digest", null, null, null, now, now);
    }

    private OrderRecord order() {
        LocalDateTime now = LocalDateTime.now();
        return new OrderRecord(1L, "ORD-1", 1L, 1L, "物理必修一", 1L, "CNY",
                OrderStatus.PAYING, "ALIPAY", null, "req", "1:1",
                now.minusMinutes(1), null, null, null, 0, null, now.minusMinutes(20), now.minusMinutes(20));
    }

    private PaymentAttemptRecord attempt() {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentAttemptRecord(1L, 1L, 1, "ALIPAY", "PROCESSING", "ORD-1_1",
                "CREATED", 1L, "digest", null, null, null, now, null);
    }
}
