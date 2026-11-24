package com.wherelee.material.modules.commerce.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReconciliationRecoveryJob {

    private final ReconciliationRepository repository;
    private final int timeoutMinutes;

    public ReconciliationRecoveryJob(ReconciliationRepository repository,
                                     @Value("${app.reconciliation.stuck-timeout-minutes:30}") int timeoutMinutes) {
        this.repository = repository;
        this.timeoutMinutes = Math.max(5, timeoutMinutes);
    }

    @Scheduled(
            fixedDelayString = "${app.reconciliation.recovery-interval-ms:300000}",
            initialDelayString = "${app.reconciliation.recovery-initial-delay-ms:60000}"
    )
    public void recover() {
        try {
            int recovered = repository.recoverStuckArtifacts(timeoutMinutes);
            if (recovered > 0) {
                log.warn("对账遗留任务已恢复 recovered={} timeoutMinutes={}", recovered, timeoutMinutes);
            }
        } catch (Exception e) {
            log.error("对账遗留任务恢复失败", e);
        }
    }
}
