package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.commerce.entitlement.EntitlementRepository;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderService;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.platform.tracing.TraceSpans;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EntitlementRepository entitlementRepository;
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
                orderService, orderRepository, paymentRepository, entitlementRepository,
                paymentGateway,
                new PaymentProperties("mock", true, "secret", 15, 15, 20, 15000, 10000, 5, 30),
                transactionManager, metrics, alertPublisher, TraceSpans.noop());
    }

    @Test
    void reconcileRetriesFailedRefundAndRevokesOrderEntitlement() {
        OrderRecord order = order();
        RefundRecord refund = refund();
        when(paymentRepository.findPendingRefunds(15, 20)).thenReturn(List.of());
        when(paymentRepository.findRetryableRefunds(5, 20)).thenReturn(List.of(refund));
        when(paymentRepository.retryRefund(1L)).thenReturn(1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentGateway.refund(order, refund, false))
                .thenReturn(new PaymentGateway.RefundResult(true, "PROVIDER-REF", null));
        when(paymentRepository.findRefundByOrder(1L)).thenReturn(Optional.of(refund));

        service.reconcile();

        verify(paymentRepository).retryRefund(1L);
        verify(paymentRepository).markRefundSucceeded(1L, "PROVIDER-REF");
        verify(orderRepository).markRefunded("ORD-1");
        verify(entitlementRepository).revokeOrder(1L, 1L, 1L, "订单退款：REF1");
    }

    private OrderRecord order() {
        LocalDateTime now = LocalDateTime.now();
        return new OrderRecord(1L, "ORD-1", 1L, 1L, "物理必修一", 1L, "CNY",
                OrderStatus.PAID, "MOCK", "TRADE-1", "req", null,
                now.plusMinutes(15), now, null, null, 0, null, now, now);
    }

    private RefundRecord refund() {
        LocalDateTime now = LocalDateTime.now();
        return new RefundRecord(1L, "REF1", 1L, 1L, "FAILED", 1,
                now.minusSeconds(1), null, "用户退款", 9L, now, null, "provider timeout");
    }
}
