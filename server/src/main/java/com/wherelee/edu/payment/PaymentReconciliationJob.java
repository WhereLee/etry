package com.wherelee.edu.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentReconciliationJob {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentReconciliationJob(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @Scheduled(
            fixedDelayString = "${app.payment.reconcile-interval-ms:15000}",
            initialDelayString = "${app.payment.reconcile-initial-delay-ms:10000}"
    )
    public void run() {
        try {
            paymentService.reconcile();
            refundService.reconcile();
        } catch (Exception e) {
            log.error("支付补偿任务执行失败", e);
        }
    }
}
