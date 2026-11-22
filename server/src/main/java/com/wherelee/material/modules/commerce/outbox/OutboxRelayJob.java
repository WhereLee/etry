package com.wherelee.material.modules.commerce.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发件箱定时投递任务，与 PaymentReconciliationJob 同构（@Scheduled + TraceSpans）。
 * 集成测试把间隔调到 1h 禁用自动投递，改由测试显式 drain，保证断言确定性。
 */
@Slf4j
@Component
public class OutboxRelayJob {

    private final OutboxRelay outboxRelay;

    public OutboxRelayJob(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.interval-ms:2000}",
            initialDelayString = "${app.outbox.initial-delay-ms:5000}"
    )
    public void run() {
        try {
            outboxRelay.drain();
        } catch (Exception e) {
            log.error("发件箱投递任务执行失败", e);
        }
    }
}