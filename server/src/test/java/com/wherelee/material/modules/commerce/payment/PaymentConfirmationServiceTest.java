package com.wherelee.material.modules.commerce.payment;

import com.wherelee.material.modules.platform.alert.AlertPublisher;
import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.outbox.OutboxRepository;
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
    @Mock private OutboxRepository outboxRepository;

    private PaymentConfirmationService service;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        service = new PaymentConfirmationService(orderRepository, paymentRepository, alertPublisher, metrics, outboxRepository);
    }

    @Test
    void successEventMarksOrderPaidOnce() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "PROCESSING"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(10L, 1, "PROCESSING")));
        stubInsertEvent(true);
        when(orderRepository.markPaid("ORD-1", "TRADE-1", "MOCK")).thenReturn(1);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.PAID, "TRADE-1")));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PAID);
        verify(paymentRepository).markAttemptSuccess(10L, "TRADE-1");
        verify(paymentRepository).markOtherProcessingSuperseded(1L, 10L);
        verify(paymentRepository).markEventProcessed(1L);
        verify(outboxRepository).enqueueFulfill(any(OrderRecord.class));
    }

    @Test
    void duplicateEventDoesNotMarkPaidAgain() {
        stubLockedOrder(OrderStatus.FULFILLED);
        stubAttempt(attempt(10L, 1, "SUCCESS"));
        stubInsertEvent(false);
        when(paymentRepository.findEvent("MOCK", "event-1")).thenReturn(Optional.of(event("PROCESSED")));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.duplicate()).isTrue();
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository, never()).markEventProcessed(anyLong());
    }

    @Test
    void amountMismatchNeverMarksPaid() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "PROCESSING"));
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 1L, 10L, "TRADE-1"));

        assertThat(result.success()).isFalse();
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).enqueueFulfill(any());
        verify(paymentRepository, never()).markAttemptSuccess(anyLong(), anyString());
        verify(paymentRepository).markEventFailed(eq(1L), contains("金额不一致"));
        verify(alertPublisher).publish(eq("PAYMENT_AMOUNT_MISMATCH"), eq("ERROR"),
                anyString(), eq(false), anyString());
    }

    @Test
    void successWithoutChannelTradeNoIsRejected() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "PROCESSING"));
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, null));

        assertThat(result.success()).isFalse();
        verify(paymentRepository, never()).markAttemptSuccess(anyLong(), anyString());
        verify(paymentRepository).markEventFailed(eq(1L), contains("缺少渠道交易号"));
    }

    @Test
    void successWithWrongMerchantTradeNoIsRejected() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "PROCESSING"));
        stubInsertEvent(true);
        PaymentNotification notification = new PaymentNotification(
                "MOCK", "event-1", 10L, "ORD-1", "PAYMENT_SUCCESS", 19900L,
                "TRADE-1", "MOCK-ORD-1-9", "NOTIFY-1", "digest", "signature");

        PaymentConfirmationService.ConfirmationResult result = service.confirm(notification);

        assertThat(result.success()).isFalse();
        verify(paymentRepository, never()).markAttemptSuccess(anyLong(), anyString());
        verify(paymentRepository).markEventFailed(eq(1L), contains("商户交易号"));
    }

    @Test
    void payFailedOrderRecoversToPaidOnVerifiedCurrentAttemptSuccess() {
        stubLockedOrder(OrderStatus.PAY_FAILED);
        stubAttempt(attempt(10L, 1, "FAILED"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(10L, 1, "FAILED")));
        stubInsertEvent(true);
        when(orderRepository.markPaid("ORD-1", "TRADE-1", "MOCK")).thenReturn(1);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.PAID, "TRADE-1")));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        verify(paymentRepository).markAttemptSuccess(10L, "TRADE-1");
        verify(orderRepository).markPaid("ORD-1", "TRADE-1", "MOCK");
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void lateFailureOnOldAttemptKeepsNewAttemptAndOrder() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "FAILED"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(20L, 2, "PROCESSING")));
        stubInsertEvent(true);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.PAYING, null)));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_FAILED", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        verify(paymentRepository).markAttemptFailed(eq(10L), anyString(), anyString());
        verify(orderRepository, never()).markPayFailed(anyString(), anyString());
        verify(paymentRepository, never()).markAttemptFailed(eq(20L), anyString(), anyString());
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void lateSuccessOnOldAttemptMarksPaidAndSupersedesOthers() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "FAILED"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(20L, 2, "PROCESSING")));
        stubInsertEvent(true);
        when(orderRepository.markPaid("ORD-1", "TRADE-1", "MOCK")).thenReturn(1);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.PAID, "TRADE-1")));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        verify(paymentRepository).markAttemptSuccess(10L, "TRADE-1");
        verify(paymentRepository).markOtherProcessingSuperseded(1L, 10L);
        verify(alertPublisher).publish(eq("PAYMENT_LATE_SUCCESS"), eq("ERROR"),
                anyString(), eq(false), anyString());
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void duplicateSuccessWithDifferentTradeNoAlertsAndKeepsOrder() {
        stubLockedOrder(OrderStatus.PAID, "TRADE-1");
        stubAttempt(attempt(20L, 2, "PROCESSING"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(20L, 2, "PROCESSING")));
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 20L, "TRADE-2"));

        assertThat(result.success()).isFalse();
        verify(alertPublisher).publish(eq("PAYMENT_DUPLICATE_SUCCESS"), eq("ERROR"),
                anyString(), eq(false), anyString());
        verify(paymentRepository).markAttemptSuccess(20L, "TRADE-2");
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void sameTradeNoSuccessOnFulfilledOrderIsIdempotent() {
        stubLockedOrder(OrderStatus.FULFILLED, "TRADE-1");
        stubAttempt(attempt(10L, 1, "SUCCESS"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(10L, 1, "SUCCESS")));
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.duplicate()).isTrue();
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void closedOrderSuccessGoesManualReviewWithoutChangingOrder() {
        stubLockedOrder(OrderStatus.CLOSED);
        stubAttempt(attempt(10L, 1, "PROCESSING"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(10L, 1, "PROCESSING")));
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isFalse();
        verify(alertPublisher).publish(eq("PAYMENT_MANUAL_REVIEW"), eq("ERROR"),
                anyString(), eq(false), anyString());
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository).markAttemptSuccess(10L, "TRADE-1");
        verify(paymentRepository).markEventManualReview(eq(1L), contains("人工核对"));
    }

    @Test
    void unknownAttemptIsRejectedWithoutFundChanges() {
        stubLockedOrder(OrderStatus.PAYING);
        when(paymentRepository.findAttemptById(99L)).thenReturn(Optional.empty());
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 99L, "TRADE-1"));

        assertThat(result.success()).isFalse();
        verify(alertPublisher).publish(eq("PAYMENT_UNKNOWN_ATTEMPT"), eq("ERROR"),
                anyString(), eq(false), anyString());
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository, never()).markAttemptSuccess(anyLong(), anyString());
        verify(paymentRepository).markEventFailed(eq(1L), contains("未知支付尝试"));
    }

    @Test
    void attemptFromAnotherOrderIsRejected() {
        stubLockedOrder(OrderStatus.PAYING);
        PaymentAttemptRecord foreign = new PaymentAttemptRecord(10L, 2L, 1, "MOCK", "PROCESSING",
                "MOCK-ORD-2-1", null, "CREATED", 19900L, "digest", null, null, null, now, null);
        stubAttempt(foreign);
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isFalse();
        verify(alertPublisher).publish(eq("PAYMENT_ATTEMPT_ORDER_MISMATCH"), eq("ERROR"),
                anyString(), eq(false), anyString());
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository, never()).markAttemptSuccess(anyLong(), anyString());
    }

    @Test
    void channelMismatchIsRejected() {
        stubLockedOrder(OrderStatus.PAYING);
        PaymentAttemptRecord alipayAttempt = new PaymentAttemptRecord(10L, 1L, 1, "ALIPAY", "PROCESSING",
                "ORD-1_1", null, "CREATED", 19900L, "digest", null, null, null, now, null);
        stubAttempt(alipayAttempt);
        stubInsertEvent(true);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isFalse();
        verify(alertPublisher).publish(eq("PAYMENT_CHANNEL_MISMATCH"), eq("ERROR"),
                anyString(), eq(false), anyString());
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
    }

    @Test
    void currentAttemptFailureMarksOrderPayFailed() {
        stubLockedOrder(OrderStatus.PAYING);
        stubAttempt(attempt(10L, 1, "PROCESSING"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(10L, 1, "PROCESSING")));
        stubInsertEvent(true);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.PAY_FAILED, null)));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_FAILED", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        verify(paymentRepository).markAttemptFailed(eq(10L), anyString(), anyString());
        verify(orderRepository).markPayFailed("ORD-1", "支付平台返回失败");
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void failureNeverOverridesSuccessAttemptOrFulfilledOrder() {
        stubLockedOrder(OrderStatus.FULFILLED, "TRADE-1");
        stubAttempt(attempt(10L, 1, "SUCCESS"));
        when(paymentRepository.findLatestAttempt(1L)).thenReturn(Optional.of(attempt(10L, 1, "SUCCESS")));
        stubInsertEvent(true);
        when(orderRepository.findByOrderNo("ORD-1")).thenReturn(Optional.of(order(OrderStatus.FULFILLED, "TRADE-1")));

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_FAILED", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isTrue();
        verify(orderRepository, never()).markPayFailed(anyString(), anyString());
        verify(paymentRepository).markEventProcessed(1L);
    }

    @Test
    void collisionWithProcessedEventDoesNotOverwriteIt() {
        stubLockedOrder(OrderStatus.PAYING);
        PaymentAttemptRecord foreign = new PaymentAttemptRecord(10L, 2L, 1, "MOCK", "PROCESSING",
                "MOCK-ORD-2-1", null, "CREATED", 19900L, "digest", null, null, null, now, null);
        stubAttempt(foreign);
        when(paymentRepository.insertEvent(anyString(), anyString(), anyLong(), anyLong(), anyString(),
                anyString(), anyLong(), any(), any(), any())).thenReturn(false);

        PaymentConfirmationService.ConfirmationResult result =
                service.confirm(notification("PAYMENT_SUCCESS", 19900L, 10L, "TRADE-1"));

        assertThat(result.success()).isFalse();
        verify(paymentRepository, never()).markEventFailed(anyLong(), anyString());
        verify(paymentRepository, never()).markEventProcessed(anyLong());
        verify(metrics).callbackCollision();
        verify(orderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(paymentRepository, never()).markAttemptSuccess(anyLong(), anyString());
    }

    private void stubLockedOrder(OrderStatus status) {
        stubLockedOrder(status, null);
    }

    private void stubLockedOrder(OrderStatus status, String providerTradeNo) {
        when(orderRepository.findByOrderNoForUpdate("ORD-1"))
                .thenReturn(Optional.of(order(status, providerTradeNo)));
    }

    private void stubAttempt(PaymentAttemptRecord attempt) {
        when(paymentRepository.findAttemptById(attempt.id())).thenReturn(Optional.of(attempt));
    }

    private void stubInsertEvent(boolean inserted) {
        when(paymentRepository.insertEvent(anyString(), anyString(), anyLong(), anyLong(), anyString(),
                anyString(), anyLong(), any(), any(), any())).thenReturn(inserted);
        when(paymentRepository.findEvent("MOCK", "event-1")).thenReturn(Optional.of(event("RECEIVED")));
    }

    private PaymentNotification notification(String eventType, long amount, Long attemptId, String tradeNo) {
        String merchantTradeNo = Long.valueOf(20L).equals(attemptId) ? "MOCK-ORD-1-2" : "MOCK-ORD-1-1";
        return new PaymentNotification("MOCK", "event-1", attemptId, "ORD-1", eventType,
                amount, tradeNo, merchantTradeNo, "NOTIFY-1", "digest", "signature");
    }

    private PaymentAttemptRecord attempt(Long id, int attemptNo, String status) {
        return new PaymentAttemptRecord(id, 1L, attemptNo, "MOCK", status,
                "MOCK-ORD-1-" + attemptNo, null, "CREATED", 19900L, "digest", null, null, null, now, null);
    }

    private PaymentEventRecord event(String status) {
        return new PaymentEventRecord(1L, "MOCK", "event-1", 10L, 1L, "ORD-1",
                "PAYMENT_SUCCESS", 19900L, "MOCK-ORD-1-1", "NOTIFY-1", status, null, now, now);
    }

    private OrderRecord order(OrderStatus status, String providerTradeNo) {
        return new OrderRecord(1L, "ORD-1", 1L, 1L, "COURSE_SERIES", "物理必修一", 19900L, "CNY", status,
                "MOCK", providerTradeNo, "req", null, now.plusMinutes(15), null, null, null,
                0, null, now, now);
    }
}
