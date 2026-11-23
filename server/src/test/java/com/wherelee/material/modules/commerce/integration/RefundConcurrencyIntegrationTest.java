package com.wherelee.material.modules.commerce.integration;

import com.wherelee.material.integration.IntegrationTestSupport;

import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.payment.RefundRecord;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Test
    void concurrentRefundsCreateOneRefundAndRevokeEntitlementOnce() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        List<CallResult<RefundRecord>> results =
                runConcurrently(10, () -> refundService.refund(order.orderNo(), fx.adminId(), "集成测试退款", false));

        drainOutbox();
        // 并发退款的安全不变量是「恰好一笔退款、权益恰好回收一次、订单终态 REFUNDED」。
        // 在 FULFILLED 窗口内到达的调用幂等返回同一笔退款；晚于退款终态到达的调用
        // 会被状态守卫合法拒绝（当前订单状态不能退款）。两种结果都正确，取决于线程调度，
        // 因此只要求至少一笔成功，且任何失败都必须是该合法拒绝，不允许出现意外错误。
        assertThat(results).anySatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results).allSatisfy(result -> {
            if (result.error() != null) {
                assertThat(result.error()).isInstanceOf(BizException.class)
                        .hasMessageContaining("当前订单状态不能退款");
            }
        });
        assertThat(count("t_refund", "order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("t_entitlement", "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND status = 'REVOKED'",
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
        jdbc.update("""
                UPDATE t_payment_attempt
                SET status = 'SUCCESS', channel_trade_no = ?, finished_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, attempt.providerTradeNo(), attempt.id());

        AtomicInteger action = new AtomicInteger();
        List<CallResult<Object>> results = runConcurrently(2, () -> {
            if (action.getAndIncrement() == 0) {
                return fulfillmentService.fulfill(order.orderNo());
            }
            return refundService.refund(order.orderNo(), fx.adminId(), "退款履约并发", false);
        });

        drainOutbox();
        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        OrderRecord current = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(current.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(paymentRepository.findRefundByOrder(orderId).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(count("t_entitlement", "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isZero();
    }

    @Test
    void legacyStyleRefundsAreGuardedPerOrderForRollbackCompatibility() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        // 模拟回滚后的旧 jar：插入 attempt_id 为 NULL 的旧式退款，同订单第二条必须被唯一键拦截
        jdbc.update("""
                INSERT INTO t_refund (refund_no, order_id, amount_cents, status, reason, requested_by)
                VALUES ('REF-LEGACY-1', ?, 199, 'PROCESSING', '旧式退款', ?)
                """, orderId, fx.adminId());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO t_refund (refund_no, order_id, amount_cents, status, reason, requested_by)
                VALUES ('REF-LEGACY-2', ?, 199, 'PROCESSING', '旧式并发退款', ?)
                """, orderId, fx.adminId()))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(count("t_refund", "order_id = ? AND attempt_id IS NULL", orderId)).isEqualTo(1);
    }

    @Test
    void attemptScopedRefundsStillCoexistUnderLegacyGuard() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        Long firstAttemptId = paymentRepository.findLatestAttempt(orderId).orElseThrow().id();
        jdbc.update("""
                INSERT INTO t_payment_attempt
                    (order_id, attempt_no, channel, status, provider_trade_no,
                     provider_status, provider_amount_cents, channel_trade_no)
                VALUES (?, 2, 'MOCK', 'SUCCESS', 'IT-SECOND-TRADE', 'TRADE_SUCCESS', 199, 'IT-SECOND-CHANNEL')
                """, orderId);
        Long secondAttemptId = paymentRepository.findLatestAttempt(orderId).orElseThrow().id();

        // 新式退款（attempt_id 非空）不受旧式防重键约束，同订单多笔可共存
        jdbc.update("""
                INSERT INTO t_refund (refund_no, order_id, attempt_id, refund_kind, amount_cents, status, reason, requested_by)
                VALUES ('REF-NEW-1', ?, ?, 'ORDER', 199, 'SUCCEEDED', '新式订单退款', ?)
                """, orderId, firstAttemptId, fx.adminId());
        jdbc.update("""
                INSERT INTO t_refund (refund_no, order_id, attempt_id, refund_kind, amount_cents, status, reason, requested_by)
                VALUES ('REF-NEW-2', ?, ?, 'DUPLICATE_PAYMENT', 199, 'SUCCEEDED', '新式重复款退款', ?)
                """, orderId, secondAttemptId, fx.adminId());
        assertThat(count("t_refund", "order_id = ?", orderId)).isEqualTo(2);
    }

    @Test
    void multiSuccessWithoutChannelTradeNoRefusesSafelyAndSurfacesForManualReview() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo()));
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        // 模拟 V15 跳过回填的历史多笔成功单：两笔 SUCCESS 都没有渠道交易号
        jdbc.update("UPDATE t_payment_attempt SET channel_trade_no = NULL WHERE order_id = ?", orderId);
        jdbc.update("""
                INSERT INTO t_payment_attempt
                    (order_id, attempt_no, channel, status, provider_trade_no,
                     provider_status, provider_amount_cents)
                VALUES (?, 2, 'MOCK', 'SUCCESS', 'IT-HIST-SECOND', 'TRADE_SUCCESS', 199)
                """, orderId);

        assertThat(paymentRepository.countMultiSuccessMissingChannelTradeNo()).isEqualTo(1);
        // 两条退款路都安全拒绝，不产生任何退款记录（资金不动）
        assertThatThrownBy(() -> refundService.refund(order.orderNo(), fx.adminId(), "历史单退款", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("多笔成功支付");
        assertThatThrownBy(() -> refundService.refundDuplicatePayment(order.orderNo(), 2, fx.adminId(), "历史重复款", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有可退款的渠道成功交易");
        assertThat(count("t_refund", "order_id = ?", orderId)).isZero();
    }
}
