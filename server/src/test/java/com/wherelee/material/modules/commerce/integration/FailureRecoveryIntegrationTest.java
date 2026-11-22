package com.wherelee.material.modules.commerce.integration;

import com.wherelee.material.integration.IntegrationTestSupport;

import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.api.PaymentDtos;
import com.wherelee.material.modules.commerce.payment.RefundRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureRecoveryIntegrationTest extends IntegrationTestSupport {

    @Test
    void fulfillmentFailureIsRecoveredByReconcile() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        PaymentDtos.MockEventResponse simulated =
                paymentService.simulate(fx.studentId(), order.orderNo(), "FULFILLMENT_FAIL_ONCE");
        assertThat(simulated.orderStatus()).isEqualTo(OrderStatus.PAID.name());
        assertThat(count("t_entitlement", "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isZero();
        assertThat(count("t_outbox_message",
                "order_id = ? AND message_type = 'FULFILL' AND status = 'NEW'", orderId)).isEqualTo(1);

        // 首次 relay 投递触发 Mock 首次履约失败，outbox 转重试，订单仍 PAID
        drainOutbox();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.PAID);

        // 补偿兜底履约成功
        paymentService.reconcile();

        OrderRecord current = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(current.status()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(count("t_entitlement", "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);

        // outbox 重试投递幂等完成（已履约，fulfill 返回 true）
        jdbc.update("UPDATE t_outbox_message SET next_retry_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(3)) WHERE order_id = ?", orderId);
        drainOutbox();
        assertThat(count("t_outbox_message",
                "order_id = ? AND message_type = 'FULFILL' AND status = 'DONE'", orderId)).isEqualTo(1);
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
