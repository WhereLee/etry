package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.platform.tracing.TraceSpans;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PaymentReconciliationJob {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final TraceSpans traceSpans;

    public PaymentReconciliationJob(PaymentService paymentService, RefundService refundService,
                                    TraceSpans traceSpans) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.traceSpans = traceSpans;
    }

    @Scheduled(
            fixedDelayString = "${app.payment.reconcile-interval-ms:15000}",
            initialDelayString = "${app.payment.reconcile-initial-delay-ms:10000}"
    )
    public void run() {
        traceSpans.trace("payment.reconcile.job", TraceSpans.tags(), () -> {
            try {
                paymentService.reconcile();
                refundService.reconcile();
            } catch (Exception e) {
                log.error("支付补偿任务执行失败", e);
            }
        });
    }
}
