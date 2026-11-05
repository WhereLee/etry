package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.PaymentDtos;
import com.wherelee.edu.modules.commerce.payment.RefundRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureRecoveryIntegrationTest extends CommerceIntegrationTestSupport {

    @Test
    void fulfillmentFailureIsRecoveredByReconcile() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        PaymentDtos.MockEventResponse simulated =
                paymentService.simulate(fx.studentId(), order.orderNo(), "FULFILLMENT_FAIL_ONCE");
        assertThat(simulated.orderStatus()).isEqualTo(OrderStatus.PAID.name());
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isZero();

        paymentService.reconcile();

        OrderRecord current = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(current.status()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
    }

    @Test
    void failedRefundIsRetriedAndCompletes() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());

        RefundRecord failed = refundService.refund(order.orderNo(), fx.adminId(), "集成测试退款", true);
        assertThat(failed.status()).isEqualTo("FAILED");
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        jdbc.update("UPDATE t_refund SET next_retry_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(3)) WHERE order_id = ?", orderId);

        refundService.reconcile();

        RefundRecord refund = paymentRepository.findRefundByOrder(orderId).orElseThrow();
        assertThat(refund.status()).isEqualTo("SUCCEEDED");
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void stuckProcessingRefundIsClaimedAndReconciled() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        jdbc.update("""
                INSERT INTO t_refund
                    (refund_no, order_id, amount_cents, status, reason, requested_by,
                     requested_at, updated_at)
                VALUES (?, ?, ?, 'PROCESSING', '模拟卡住退款', ?,
                        TIMESTAMPADD(SECOND, -120, CURRENT_TIMESTAMP(3)),
                        TIMESTAMPADD(SECOND, -120, CURRENT_TIMESTAMP(3)))
                """, "REF-IT-STUCK", orderId, fx.priceCents(), fx.adminId());
        jdbc.update("UPDATE t_order SET status = 'REFUNDING' WHERE id = ?", orderId);

        refundService.reconcile();

        RefundRecord refund = paymentRepository.findRefundByOrder(orderId).orElseThrow();
        assertThat(refund.status()).isEqualTo("SUCCEEDED");
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.REFUNDED);
    }
}
