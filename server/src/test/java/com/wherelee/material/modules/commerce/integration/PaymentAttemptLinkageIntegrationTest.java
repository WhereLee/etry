package com.wherelee.material.modules.commerce.integration;

import com.wherelee.material.integration.IntegrationTestSupport;

import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.payment.PaymentAttemptRecord;
import com.wherelee.material.modules.commerce.payment.PaymentConfirmationService;
import com.wherelee.material.modules.commerce.payment.PaymentNotification;
import com.wherelee.material.modules.commerce.payment.RefundKind;
import com.wherelee.material.modules.commerce.payment.RefundRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付尝试关联与迟到通知隔离：事件按自身 attemptId 落账，迟到老通知不得污染新尝试。
 */
class PaymentAttemptLinkageIntegrationTest extends IntegrationTestSupport {

    @Test
    void lateFailureAfterNewAttemptDoesNotTouchNewAttemptOrOrder() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        PaymentAttemptRecord attempt1 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(failureNotification(order.orderNo(), attempt1));
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());
        assertThat(attempt2.attemptNo()).isEqualTo(2);

        PaymentConfirmationService.ConfirmationResult result = paymentService.acceptVerified(
                notification(order.orderNo(), attempt1, "PAYMENT_FAILED", "1:LATE_FAILED",
                        attempt1.providerTradeNo(), null));

        assertThat(result.success()).isTrue();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.PAYING);
        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("PROCESSING");
        assertThat(paymentRepository.findAttemptById(attempt1.id()).orElseThrow().status())
                .isEqualTo("FAILED");
    }

    @Test
    void lateSuccessOnOldAttemptFulfillsOnceAndSupersedesNewAttempt() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        PaymentAttemptRecord attempt1 = failedFirstAttempt(order.orderNo());
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());

        PaymentConfirmationService.ConfirmationResult result =
                paymentService.acceptVerified(successNotification(order.orderNo(), attempt1));

        drainOutbox();
        assertThat(result.success()).isTrue();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(paymentRepository.findAttemptById(attempt1.id()).orElseThrow().status())
                .isEqualTo("SUCCESS");
        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("SUPERSEDED");
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(count("t_alert_log", "alert_type = 'PAYMENT_LATE_SUCCESS'")).isEqualTo(1);
        assertThat(count("t_payment_event",
                "attempt_id = ? AND event_type = 'PAYMENT_SUCCESS' AND process_status = 'PROCESSED'", attempt1.id()))
                .isEqualTo(1);
    }

    @Test
    void secondSuccessAfterLateSuccessIsDuplicatePaymentWithoutSecondEntitlement() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        PaymentAttemptRecord attempt1 = failedFirstAttempt(order.orderNo());
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt1));

        PaymentConfirmationService.ConfirmationResult result =
                paymentService.acceptVerified(successNotification(order.orderNo(), attempt2));

        drainOutbox();
        assertThat(result.success()).isFalse();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(count("t_alert_log", "alert_type = 'PAYMENT_DUPLICATE_SUCCESS'")).isEqualTo(1);
        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("SUCCESS");
    }

    @Test
    void tradeFinishedAfterTradeSuccessDoesNotRefulfill() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());
        completePayment(order.orderNo());

        PaymentConfirmationService.ConfirmationResult result = paymentService.acceptVerified(
                notification(order.orderNo(), attempt, "PAYMENT_SUCCESS", "1:TRADE_FINISHED",
                        attempt.providerTradeNo(), null));

        assertThat(result.success()).isTrue();
        assertThat(result.duplicate()).isTrue();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(count("t_payment_event", "order_no = ?", order.orderNo())).isEqualTo(2);
        assertThat(count("t_payment_event", "order_no = ? AND process_status = 'PROCESSED'", order.orderNo()))
                .isEqualTo(2);
    }

    @Test
    void redeliveryWithDifferentNotifyIdCreatesOneBusinessEvent() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());

        paymentService.acceptVerified(notification(order.orderNo(), attempt, "PAYMENT_SUCCESS",
                "1:PAID", attempt.providerTradeNo(), "NOTIFY-A"));
        PaymentConfirmationService.ConfirmationResult result = paymentService.acceptVerified(
                notification(order.orderNo(), attempt, "PAYMENT_SUCCESS",
                        "1:PAID", attempt.providerTradeNo(), "NOTIFY-B"));

        drainOutbox();
        assertThat(result.duplicate()).isTrue();
        assertThat(count("t_payment_event", "order_no = ?", order.orderNo())).isEqualTo(1);
        assertThat(activeEntitlements()).isEqualTo(1);
        String notifyId = jdbc.queryForObject(
                "SELECT provider_notify_id FROM t_payment_event WHERE order_no = ?",
                String.class, order.orderNo());
        assertThat(notifyId).isEqualTo("NOTIFY-A");
    }

    @Test
    void forgedAttemptIdFromOtherOrderIsRejectedWithoutChangingOrder() {
        OrderDtos.OrderView orderA = createOrder(nextRequestId());
        pay(orderA.orderNo());
        Long series2 = insertSecondSeries();
        OrderDtos.OrderView orderB = orderService.create(fx.studentId(),
                new OrderDtos.CreateOrderRequest(series2, nextRequestId()));
        pay(orderB.orderNo());
        PaymentAttemptRecord attemptB = latestAttempt(orderB.orderNo());

        PaymentConfirmationService.ConfirmationResult result = paymentService.acceptVerified(
                notification(orderA.orderNo(), attemptB, "PAYMENT_SUCCESS", "FORGED",
                        attemptB.providerTradeNo(), null));

        assertThat(result.success()).isFalse();
        assertThat(orderRepository.findByOrderNo(orderA.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.PAYING);
        assertThat(paymentRepository.findAttemptById(attemptB.id()).orElseThrow().status())
                .isEqualTo("PROCESSING");
        assertThat(count("t_payment_event", "order_no = ? AND process_status = 'FAILED'", orderA.orderNo()))
                .isEqualTo(1);
        assertThat(count("t_alert_log", "alert_type = 'PAYMENT_ATTEMPT_ORDER_MISMATCH'")).isEqualTo(1);
        assertThat(activeEntitlements()).isZero();
    }

    @Test
    void reconcileClosesSupersededOldAttempt() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        PaymentAttemptRecord attempt1 = failedFirstAttempt(order.orderNo());
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt1));
        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("SUPERSEDED");

        drainOutbox();
        paymentService.reconcile();

        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("CLOSED");
        assertThat(paymentRepository.findAttemptById(attempt1.id()).orElseThrow().status())
                .isEqualTo("SUCCESS");
        assertThat(activeEntitlements()).isEqualTo(1);
    }

    @Test
    void collisionWithProcessedEventKeepsOriginalProcessed() {
        OrderDtos.OrderView orderA = createOrder(nextRequestId());
        completePayment(orderA.orderNo());
        PaymentAttemptRecord attemptA = latestAttempt(orderA.orderNo());
        Long series2 = insertSecondSeries();
        OrderDtos.OrderView orderB = orderService.create(fx.studentId(),
                new OrderDtos.CreateOrderRequest(series2, nextRequestId()));
        pay(orderB.orderNo());
        PaymentAttemptRecord attemptB = latestAttempt(orderB.orderNo());

        // 相同 event_key 但 attemptId 属于另一订单：归属拒绝，碰撞不得改坏原 PROCESSED 事件
        String processedKey = "IT:" + orderA.orderNo() + ":1:PAID";
        PaymentNotification forged = new PaymentNotification("MOCK", processedKey, attemptB.id(),
                orderA.orderNo(), "PAYMENT_SUCCESS", fx.priceCents(), attemptB.providerTradeNo(),
                attemptB.providerTradeNo(), null, null, null);
        PaymentConfirmationService.ConfirmationResult result = paymentService.acceptVerified(forged);

        assertThat(result.success()).isFalse();
        String status = jdbc.queryForObject(
                "SELECT process_status FROM t_payment_event WHERE provider = 'MOCK' AND event_key = ?",
                String.class, processedKey);
        assertThat(status).isEqualTo("PROCESSED");
        assertThat(orderRepository.findByOrderNo(orderA.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(paymentRepository.findAttemptById(attemptA.id()).orElseThrow().status())
                .isEqualTo("SUCCESS");
        assertThat(paymentRepository.findAttemptById(attemptB.id()).orElseThrow().status())
                .isEqualTo("PROCESSING");
        assertThat(activeEntitlements()).isEqualTo(1);
    }

    @Test
    void reconcileFindsPaidSupersededAttemptWhenCallbackIsLost() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        PaymentAttemptRecord attempt1 = failedFirstAttempt(order.orderNo());
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt1));
        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("SUPERSEDED");

        drainOutbox();
        jdbc.update("""
                UPDATE t_payment_attempt
                SET provider_status = 'PAID', provider_amount_cents = ?
                WHERE id = ?
                """, order.priceCents(), attempt2.id());

        paymentService.reconcile();

        assertThat(paymentRepository.findAttemptById(attempt2.id()).orElseThrow().status())
                .isEqualTo("SUCCESS");
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(count("t_alert_log", "alert_type = 'PAYMENT_DUPLICATE_SUCCESS'")).isEqualTo(1);
        assertThat(count("t_payment_event", "attempt_id = ? AND process_status = 'PROCESSED'",
                attempt2.id())).isEqualTo(1);
    }

    @Test
    void duplicatePaymentRefundDoesNotRevokeEntitlementAndOrderRefundStillWorks() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        PaymentAttemptRecord attempt1 = failedFirstAttempt(order.orderNo());
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt1));
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt2));
        drainOutbox();
        assertThat(paymentRepository.countUnrefundedDuplicatePayments()).isEqualTo(1);

        RefundRecord duplicateRefund = refundService.refundDuplicatePayment(
                order.orderNo(), attempt2.attemptNo(), fx.adminId(), "退还重复付款", false);

        assertThat(duplicateRefund.refundKind()).isEqualTo(RefundKind.DUPLICATE_PAYMENT);
        assertThat(duplicateRefund.status()).isEqualTo("SUCCEEDED");
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(paymentRepository.countUnrefundedDuplicatePayments()).isZero();

        RefundRecord orderRefund = refundService.refund(
                order.orderNo(), fx.adminId(), "退还主支付", false);
        drainOutbox();

        assertThat(orderRefund.refundKind()).isEqualTo(RefundKind.ORDER);
        assertThat(orderRefund.status()).isEqualTo("SUCCEEDED");
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.REFUNDED);
        assertThat(activeEntitlements()).isZero();
        assertThat(count("t_refund", "order_id = ?", orderRepository.findByOrderNo(order.orderNo())
                .orElseThrow().id())).isEqualTo(2);
    }

    private PaymentAttemptRecord failedFirstAttempt(String orderNo) {
        pay(orderNo);
        PaymentAttemptRecord attempt1 = latestAttempt(orderNo);
        paymentService.acceptVerified(failureNotification(orderNo, attempt1));
        return paymentRepository.findAttemptById(attempt1.id()).orElseThrow();
    }

    private PaymentAttemptRecord latestAttempt(String orderNo) {
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().id();
        return paymentRepository.findLatestAttempt(orderId).orElseThrow();
    }

    private PaymentNotification notification(String orderNo, PaymentAttemptRecord attempt, String eventType,
                                             String keySuffix, String tradeNo, String notifyId) {
        var order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        return new PaymentNotification(
                "MOCK", "IT:" + orderNo + ":" + keySuffix, attempt.id(), orderNo, eventType,
                order.priceCents(), tradeNo, attempt.providerTradeNo(), notifyId, null, null);
    }

    private int activeEntitlements() {
        return count("t_entitlement", "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId());
    }

    private Long insertSecondSeries() {
        jdbc.update("""
                INSERT INTO t_series
                    (teacher_id, title, summary, sort, status, published_at,
                     sale_status, price_cents, currency, sale_updated_at)
                VALUES (?, 'IT 系列二', '集成测试系列', 2, 'PUBLISHED', CURRENT_TIMESTAMP(3),
                        'ON_SALE', ?, 'CNY', CURRENT_TIMESTAMP(3))
                """, fx.teacherId(), fx.priceCents());
        return jdbc.queryForObject("SELECT id FROM t_series WHERE title = 'IT 系列二'", Long.class);
    }
}
