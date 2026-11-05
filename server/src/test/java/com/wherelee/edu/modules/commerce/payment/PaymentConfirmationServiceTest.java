package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AlertPublisher alertPublisher;
    @Mock private PaymentMetrics metrics;

    private PaymentConfirmationService service;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        service = new PaymentConfirmationService(orderRepository, paymentRepository, alertPublisher, metrics);
    }

    @Test
    void successEventMarksOrderPaidOnce() {
        OrderRecord paying = order(OrderStatus.PAYING);
        OrderRecord paid = order(OrderStatus.PAID);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(paying), Optional.of(paid));
        when(paymentRepository.insertEvent(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyLong(), any())).thenReturn(true);
        when(paymentRepository.findEvent("MOCK", "event-1")).thenReturn(Optional.of(event("RECEIVED")));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.empty());
        when(orderRepository.markPaid("ORD-1", "TRADE-1", "MOCK")).thenReturn(1);

        PaymentConfirmationService.ConfirmationResult result = service.confirm(notification(19900L));

        assertThat(result.success()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PAID);
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void duplicateEventDoesNotMarkPaidAgain() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.FULFILLED)));
        when(paymentRepository.insertEvent(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyLong(), any())).thenReturn(false);
        when(paymentRepository.findEvent("MOCK", "event-1")).thenReturn(Optional.of(event("PROCESSED")));

        PaymentConfirmationService.ConfirmationResult result = service.confirm(notification(19900L));

        assertThat(result.duplicate()).isTrue();
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository, never()).markEventProcessed(anyLong());
    }

    @Test
    void amountMismatchNeverMarksPaid() {
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.PAYING)));
        when(paymentRepository.insertEvent(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyLong(), any())).thenReturn(true);
        when(paymentRepository.findEvent("MOCK", "event-1")).thenReturn(Optional.of(event("RECEIVED")));

        PaymentConfirmationService.ConfirmationResult result = service.confirm(notification(1L));

        assertThat(result.success()).isFalse();
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository).markEventFailed(eq(1L), contains("金额不一致"));
        verify(alertPublisher).publish(eq("PAYMENT_AMOUNT_MISMATCH"), eq("ERROR"),
                anyString(), eq(false), anyString());
    }

    private PaymentNotification notification(long amount) {
        return new PaymentNotification("MOCK", "event-1", "ORD-1", "PAYMENT_SUCCESS",
                amount, "TRADE-1", "signature");
    }

    private PaymentEventRecord event(String status) {
        return new PaymentEventRecord(1L, "MOCK", "event-1", 1L, "ORD-1",
                "PAYMENT_SUCCESS", 19900L, status, null, now, now);
    }

    private OrderRecord order(OrderStatus status) {
        return new OrderRecord(1L, "ORD-1", 1L, 1L, "物理必修一", 19900L, "CNY", status,
                "MOCK", null, "req", null, now.plusMinutes(15), null, null, null,
                0, null, now, now);
    }
}
