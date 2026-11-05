package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.RefundRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RefundConcurrencyIntegrationTest extends CommerceIntegrationTestSupport {

    @Test
    void concurrentRefundsCreateOneRefundAndRevokeEntitlementOnce() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        List<CallResult<RefundRecord>> results =
                runConcurrently(10, () -> refundService.refund(order.orderNo(), fx.adminId(), "集成测试退款", false));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(count("t_refund", "order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'REVOKED'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);

        OrderRecord current = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(current.status()).isEqualTo(OrderStatus.REFUNDED);
        RefundRecord refund = paymentRepository.findRefundByOrder(orderId).orElseThrow();
        assertThat(refund.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void refundAndFulfillmentRaceKeepsRefundedTerminalState() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        var attempt = paymentRepository.findLatestAttempt(orderId).orElseThrow();
        jdbc.update("""
                UPDATE t_order
                SET status = 'PAID', paid_at = CURRENT_TIMESTAMP(3), provider_trade_no = ?
                WHERE id = ?
                """, attempt.providerTradeNo(), orderId);
        jdbc.update("UPDATE t_payment_attempt SET status = 'SUCCESS', finished_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                attempt.id());

        AtomicInteger action = new AtomicInteger();
        List<CallResult<Object>> results = runConcurrently(2, () -> {
            if (action.getAndIncrement() == 0) {
                return fulfillmentService.fulfill(order.orderNo());
            }
            return refundService.refund(order.orderNo(), fx.adminId(), "退款履约并发", false);
        });

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        OrderRecord current = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(current.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(paymentRepository.findRefundByOrder(orderId).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isZero();
    }
}
