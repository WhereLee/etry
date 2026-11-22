package com.wherelee.material.modules.commerce.payment;

import com.wherelee.material.modules.commerce.outbox.OutboxRepository;
import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import com.wherelee.material.modules.commerce.order.OrderService;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.platform.alert.AlertPublisher;
import com.wherelee.material.modules.platform.tracing.TraceSpans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private PaymentMetrics metrics;
    @Mock private AlertPublisher alertPublisher;

    private RefundService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new RefundService(
                orderService, orderRepository, paymentRepository, outboxRepository,
                paymentGateway,
                new PaymentProperties("mock", true, "secret", 15, 15, 20, 15000, 10000, 5, 30),
                transactionManager, metrics, alertPublisher, TraceSpans.noop());
    }

    @Test
    void reconcileRetriesFailedRefundAndEnqueuesRevoke() {
        OrderRecord order = order(OrderStatus.PAID);
        PaymentAttemptRecord attempt = attempt();
        RefundRecord refund = refund(RefundKind.ORDER);
        when(paymentRepository.findRefundsForReconcile(15, 5, 20)).thenReturn(List.of(refund));
        when(paymentRepository.claimRefund(eq(1L), anyString(), anyInt())).thenReturn(1);
        when(paymentRepository.findRefundByIdForUpdate(1L)).thenReturn(Optional.of(refund));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAttemptById(1L)).thenReturn(Optional.of(attempt));
        when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentGateway.refund(order, attempt, refund, false))
                .thenReturn(new PaymentGateway.RefundResult(true, "REF1", null));
        when(paymentRepository.markRefundSucceeded(1L, "REF1")).thenReturn(1);

        service.reconcile();

        verify(paymentRepository).markRefundSucceeded(1L, "REF1");
        verify(orderRepository).markRefunded("ORD-1");
        verify(outboxRepository).enqueueRevoke(any(OrderRecord.class), eq("订单退款：REF1"));
    }

    @Test
    void duplicateRefundFailureNeverChangesOrderOrEntitlement() {
        OrderRecord order = order(OrderStatus.FULFILLED);
        PaymentAttemptRecord primary = attempt();
        PaymentAttemptRecord duplicate = new PaymentAttemptRecord(
                2L, 1L, 2, "MOCK", "SUCCESS", "MOCK-ORD-1-2", "TRADE-2",
                "CREATED", 19900L, "digest", null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
        RefundRecord refund = new RefundRecord(2L, "REF2", 1L, 2L, RefundKind.DUPLICATE_PAYMENT,
                19900L, "PROCESSING", 0, null, null, "重复付款退款", 9L,
                LocalDateTime.now(), null, null);
        when(orderService.require("ORD-1")).thenReturn(order);
        when(paymentRepository.findAttempts(1L)).thenReturn(List.of(primary, duplicate));
        when(paymentRepository.findRefundByAttempt(2L)).thenReturn(Optional.of(refund));
        when(paymentRepository.claimRefundManual(eq(2L), anyString(), anyInt())).thenReturn(1);
        when(paymentRepository.findRefundByIdForUpdate(2L)).thenReturn(Optional.of(refund));
        when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentGateway.refund(order, duplicate, refund, true))
                .thenReturn(new PaymentGateway.RefundResult(false, null, "退款失败"));
        when(paymentRepository.markRefundFailed(eq(2L), anyString(), eq("退款失败"), any()))
                .thenReturn(1);

        service.refundDuplicatePayment("ORD-1", 2, 9L, "重复付款退款", true);

        verify(paymentRepository).markRefundFailed(eq(2L), anyString(), eq("退款失败"), any());
        verify(orderRepository, never()).markRefunded(anyString());
        verify(orderRepository, never()).markRefundFailed(anyString(), anyString());
        verify(outboxRepository, never()).enqueueRevoke(any(), anyString());
    }

    private OrderRecord order(OrderStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderRecord(1L, "ORD-1", 1L, 1L, "COURSE_SERIES", "物理必修一", 19900L, "CNY",
                status, "MOCK", "TRADE-1", "req", null,
                now.plusMinutes(15), now, null, null, 0, null, now, now);
    }

    private PaymentAttemptRecord attempt() {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentAttemptRecord(1L, 1L, 1, "MOCK", "SUCCESS", "MOCK-ORD-1-1",
                "TRADE-1", "CREATED", 19900L, "digest", null, null, null, now, now);
    }

    private RefundRecord refund(RefundKind kind) {
        LocalDateTime now = LocalDateTime.now();
        return new RefundRecord(1L, "REF1", 1L, 1L, kind, 19900L, "FAILED", 1,
                now.minusSeconds(1), null, "用户退款", 9L, now, null, "provider timeout");
    }
}
