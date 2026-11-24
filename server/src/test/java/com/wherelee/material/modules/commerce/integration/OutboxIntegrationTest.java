package com.wherelee.material.modules.commerce.integration;

import com.wherelee.material.integration.IntegrationTestSupport;

import com.wherelee.material.modules.commerce.order.OrderStatus;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.outbox.OutboxMessage;
import com.wherelee.material.modules.commerce.outbox.OutboxReplayService;
import com.wherelee.material.modules.commerce.payment.EntitlementConsistencyJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事务性发件箱集成测试：崩溃重放、重复投递幂等、relay 宕机租约重认领、
 * 重试耗尽 DEAD 转人工、退款撤销异步投递（最终一致窗口）。
 */
class OutboxIntegrationTest extends IntegrationTestSupport {

    @Autowired private OutboxReplayService outboxReplayService;
    @Autowired private EntitlementConsistencyJob entitlementConsistencyJob;

    @Test
    void crashBetweenCommitAndDeliveryIsRecoveredByRelay() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        // confirm 事务提交：钱已落定 + 履约意图持久化，但进程在投递前崩溃（不 drain）
        paymentService.acceptVerified(successNotification(order.orderNo()));
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.PAID);
        assertThat(activeEntitlements()).isZero();
        OutboxMessage pending = outboxRepository
                .findByOrderAndType(orderId, OutboxMessage.TYPE_FULFILL).orElseThrow();
        assertThat(pending.status()).isEqualTo(OutboxMessage.STATUS_NEW);
        assertThat(pending.productType()).isEqualTo("COURSE_SERIES");
        assertThat(pending.productId()).isEqualTo(fx.seriesId());
        assertThat(pending.userId()).isEqualTo(fx.studentId());

        // relay 重启后补投，意图不丢
        drainOutbox();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_FULFILL).orElseThrow().status())
                .isEqualTo(OutboxMessage.STATUS_DONE);
    }

    @Test
    void duplicateDeliveryIsIdempotent() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        assertThat(activeEntitlements()).isEqualTo(1);

        // 模拟 relay 重复投递一条已完成的消息
        jdbc.update("""
                UPDATE t_outbox_message SET status = 'NEW', retry_count = 0, lease_token = NULL,
                    lease_until = NULL, next_retry_at = NULL, processed_at = NULL WHERE order_id = ?
                """, orderId);
        drainOutbox();

        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(count("t_entitlement", "user_id = ? AND product_id = ? AND source = 'ORDER'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_FULFILL).orElseThrow().status())
                .isEqualTo(OutboxMessage.STATUS_DONE);
    }

    @Test
    void concurrentRelaysClaimDisjointBatches() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo()));
        outboxRepository.enqueueRevoke(
                orderRepository.findByOrderNo(order.orderNo()).orElseThrow(), "并发认领测试");

        List<CallResult<List<OutboxMessage>>> results = runConcurrently(2,
                () -> outboxRepository.claimBatch("it-" + UUID.randomUUID(), 1, 60));
        List<Long> claimedIds = results.stream()
                .filter(CallResult::success)
                .flatMap(result -> result.value().stream())
                .map(OutboxMessage::id)
                .toList();

        assertThat(claimedIds).hasSize(2).doesNotHaveDuplicates();
        assertThat(count("t_outbox_message", "status = 'PROCESSING'")).isEqualTo(2);
    }

    @Test
    void expiredLeaseIsReclaimedByAnotherRelay() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        paymentService.acceptVerified(successNotification(order.orderNo()));

        // 模拟某 relay 认领后宕机：PROCESSING + 租约过期，尚未投递
        jdbc.update("""
                UPDATE t_outbox_message SET status = 'PROCESSING', lease_token = 'dead-worker',
                    retry_count = 1, lease_until = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(3))
                WHERE order_id = ?
                """, orderId);
        assertThat(activeEntitlements()).isZero();

        // 另一 relay 重认领过期租约并投递
        drainOutbox();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_FULFILL).orElseThrow().status())
                .isEqualTo(OutboxMessage.STATUS_DONE);
    }

    @Test
    void poisonMessageRetriesThenDead() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        paymentService.acceptVerified(successNotification(order.orderNo()));

        // 订单进入非终态的不可履约态（REFUNDING），履约永远失败；缩短重试上限快速耗尽。
        // 终态（FULFILLED/REFUNDED/CLOSED）已改为幂等跳过，不再构成毒消息。
        jdbc.update("UPDATE t_order SET status = 'REFUNDING' WHERE id = ?", orderId);
        jdbc.update("UPDATE t_outbox_message SET max_retry = 2 WHERE order_id = ?", orderId);
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    UPDATE t_outbox_message SET next_retry_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(3))
                    WHERE order_id = ? AND status <> 'DEAD'
                    """, orderId);
            outboxRelay.drain();
        }

        assertThat(count("t_outbox_message", "order_id = ? AND status = 'DEAD'", orderId)).isEqualTo(1);
        assertThat(count("t_alert_log", "alert_type = 'OUTBOX_DEAD'")).isEqualTo(1);
        assertThat(activeEntitlements()).isZero();
    }

    @Test
    void refundEnqueuesRevokeDeliveredByRelay() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        refundService.refund(order.orderNo(), fx.adminId(), "outbox 退款测试", false);
        // 退款事务已提交：订单 REFUNDED + 撤销意图持久化，权益尚未撤销（最终一致窗口）
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.REFUNDED);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_REVOKE).orElseThrow().status())
                .isEqualTo(OutboxMessage.STATUS_NEW);

        drainOutbox();
        assertThat(activeEntitlements()).isZero();
        assertThat(count("t_entitlement",
                "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND source = 'ORDER' AND status = 'REVOKED'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
        assertThat(outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_REVOKE).orElseThrow().status())
                .isEqualTo(OutboxMessage.STATUS_DONE);
    }

    @Test
    void manualReplayOfDeliveredOrDeadMessageIsIdempotent() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        Long outboxId = outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_FULFILL)
                .orElseThrow().id();

        jdbc.update("UPDATE t_outbox_message SET status = 'DEAD', last_error = 'drill' WHERE id = ?", outboxId);
        OutboxMessage replayed = outboxReplayService.replay(outboxId);

        assertThat(replayed.status()).isEqualTo(OutboxMessage.STATUS_DONE);
        assertThat(activeEntitlements()).isEqualTo(1);
        assertThat(count("t_entitlement", "source_order_id = ? AND status = 'ACTIVE'", orderId)).isEqualTo(1);
    }

    @Test
    void refundedOrderWithActiveEntitlementIsCompensatedEvenWhenRevokeIsDead() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();
        refundService.refund(order.orderNo(), fx.adminId(), "权益补偿测试", false);
        Long revokeId = outboxRepository.findByOrderAndType(orderId, OutboxMessage.TYPE_REVOKE)
                .orElseThrow().id();
        jdbc.update("UPDATE t_outbox_message SET status = 'DEAD', last_error = 'lost' WHERE id = ?", revokeId);
        assertThat(activeEntitlements()).isEqualTo(1);

        entitlementConsistencyJob.reconcile();

        assertThat(activeEntitlements()).isZero();
        assertThat(count("t_entitlement", "source_order_id = ? AND status = 'REVOKED'", orderId)).isEqualTo(1);
    }

    private int activeEntitlements() {
        return count("t_entitlement", "user_id = ? AND product_id = ? AND product_type = 'COURSE_SERIES' AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId());
    }
}