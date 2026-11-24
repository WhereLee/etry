package com.wherelee.material.modules.commerce.integration;

import com.wherelee.material.integration.IntegrationTestSupport;

import com.wherelee.material.modules.commerce.api.AdminReconciliationOperations;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.payment.PaymentAttemptRecord;
import com.wherelee.material.modules.commerce.payment.PaymentRepository;
import com.wherelee.material.modules.commerce.reconciliation.ReconciliationRecoveryJob;
import com.wherelee.material.modules.commerce.reconciliation.ReconciliationRepository;
import com.wherelee.material.modules.commerce.reconciliation.ReconciliationService;
import com.wherelee.material.modules.platform.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationIntegrationTest extends IntegrationTestSupport {

    @Autowired private ReconciliationService reconciliationService;
    @Autowired private ReconciliationRepository reconciliationRepository;
    @Autowired private ReconciliationRecoveryJob reconciliationRecoveryJob;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    void exactStatementMatchesLocalPayment() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());

        AdminReconciliationOperations.StatementView file = upload("bill.csv",
                row(attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents(), "交易成功"));
        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());

        assertThat(run.status()).isEqualTo("COMPLETED");
        assertThat(run.matchedCount()).isEqualTo(1);
        assertThat(run.breakCount()).isZero();
    }

    @Test
    void amountMismatchCreatesCriticalBreak() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());
        AdminReconciliationOperations.StatementView file = upload("mismatch.csv",
                row(attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents() + 100, "交易成功"));

        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());
        AdminReconciliationOperations.BreakView item = reconciliationService.breaks(run.id(), null, 1, 20)
                .records().get(0);

        assertThat(run.breakCount()).isEqualTo(1);
        assertThat(item.breakType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(item.severity()).isEqualTo("CRITICAL");
        assertThat(item.deltaCents()).isEqualTo(100);
    }

    @Test
    void providerOnlyAndLocalOnlyAreBothReported() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        AdminReconciliationOperations.StatementView file = upload("orphan.csv",
                row("CHANNEL-ORPHAN", "LOCAL-ORPHAN", 123, "交易成功"));

        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());
        List<AdminReconciliationOperations.BreakView> rows = reconciliationService
                .breaks(run.id(), null, 1, 20).records();

        assertThat(rows).extracting(AdminReconciliationOperations.BreakView::breakType)
                .contains("PROVIDER_ONLY", "LOCAL_ONLY");
    }

    @Test
    void reconciliationBreakCanBeResolvedWithAuditAction() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());
        AdminReconciliationOperations.StatementView file = upload("resolve.csv",
                row(attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents() + 1, "交易成功"));
        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());
        AdminReconciliationOperations.BreakView breakRow = reconciliationService.breaks(run.id(), null, 1, 20).records().get(0);

        AdminReconciliationOperations.BreakView resolved = reconciliationService.actOnBreak(
                breakRow.id(), "RESOLVE", "CONFIRMED", "人工核对完成", fx.adminId());

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.resolutionCode()).isEqualTo("CONFIRMED");
        assertThat(count("t_recon_action", "break_id = ?", breakRow.id())).isEqualTo(1);
    }

    @Test
    void duplicateSuccessfulPaymentsOnSameOrderCreateDuplicateBreak() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        PaymentAttemptRecord attempt1 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(failureNotification(order.orderNo(), attempt1));
        pay(order.orderNo());
        PaymentAttemptRecord attempt2 = latestAttempt(order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt1));
        paymentService.acceptVerified(successNotification(order.orderNo(), attempt2));

        AdminReconciliationOperations.StatementView file = upload("duplicate.csv",
                row(attempt1.providerTradeNo(), attempt1.providerTradeNo(), order.priceCents(), "交易成功"),
                row(attempt2.providerTradeNo(), attempt2.providerTradeNo(), order.priceCents(), "交易成功"));
        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());
        List<AdminReconciliationOperations.BreakView> rows = reconciliationService
                .breaks(run.id(), null, 1, 20).records();

        assertThat(rows).extracting(AdminReconciliationOperations.BreakView::breakType)
                .contains("DUPLICATE_PAYMENT");
    }

    @Test
    void refundStatementMatchesLocalSucceededRefund() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());
        refundService.refund(order.orderNo(), fx.adminId(), "对账退款测试", false);
        String time = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String paymentRow = String.format(java.util.Locale.ROOT, "%s,%s,%.2f,交易成功,交易,%s",
                attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents() / 100.0, time);
        String refundRow = String.format(java.util.Locale.ROOT, "%s,%s,-%.2f,退款成功,退款,%s",
                attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents() / 100.0, time);
        AdminReconciliationOperations.StatementView file = upload("refund.csv", paymentRow, refundRow);

        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());

        assertThat(run.status()).isEqualTo("COMPLETED");
        assertThat(run.matchedCount()).isEqualTo(2);
        assertThat(run.breakCount()).isZero();
    }

    @Test
    void duplicateBreakIdentityIsIdempotentWithinRun() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());
        AdminReconciliationOperations.StatementView file = upload("break-idempotent.csv",
                row(attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents() + 100, "交易成功"));
        AdminReconciliationOperations.RunView run = reconciliationService.run(file.id(), fx.adminId());
        AdminReconciliationOperations.BreakView breakRow = reconciliationService.breaks(run.id(), null, 1, 20)
                .records().get(0);
        Long orderId = orderRepository.findByOrderNo(order.orderNo()).orElseThrow().id();

        reconciliationRepository.insertBreak(run.id(), breakRow.itemId(), breakRow.breakType(),
                breakRow.severity(), orderId, order.orderNo(), breakRow.attemptId(),
                breakRow.providerTradeNo(), breakRow.merchantTradeNo(),
                breakRow.localAmountCents(), breakRow.providerAmountCents(), breakRow.deltaCents(), breakRow.detail());

        assertThat(count("t_recon_break", "run_id = ?", run.id())).isEqualTo(1);
    }

    @Test
    void concurrentRunsForSameFileAreSerialized() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        PaymentAttemptRecord attempt = latestAttempt(order.orderNo());
        AdminReconciliationOperations.StatementView file = upload("concurrent-runs.csv",
                row(attempt.providerTradeNo(), attempt.providerTradeNo(), order.priceCents(), "交易成功"));

        var results = runConcurrently(2, () -> reconciliationService.run(file.id(), fx.adminId()));
        var runs = results.stream().filter(CallResult::success).map(CallResult::value).toList();

        assertThat(runs).hasSize(2);
        assertThat(runs).extracting(AdminReconciliationOperations.RunView::runVersion)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(count("t_recon_statement_item", "file_id = ?", file.id())).isEqualTo(1);
        assertThat(count("t_recon_match", "run_id IN (SELECT id FROM t_recon_run WHERE file_id = ?)", file.id()))
                .isEqualTo(2);
    }

    @Test
    void invalidBillRollsBackFileRecord() {
        byte[] invalid = "bad,bill\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reconciliationService.ingest(
                "ALIPAY", LocalDate.now(), "invalid-rollback.csv", "text/csv", invalid, fx.adminId()))
                .isInstanceOf(BizException.class);

        assertThat(count("t_recon_statement_file", "file_name = 'invalid-rollback.csv'")).isZero();
    }

    @Test
    void legacyStuckParsingAndRunningAreRecovered() {
        AdminReconciliationOperations.StatementView file = upload("stuck-recovery.csv",
                row("CHANNEL-STUCK", "LOCAL-STUCK", 123, "交易成功"));
        jdbc.update("""
                UPDATE t_recon_statement_file SET status = 'PARSING',
                    created_at = TIMESTAMPADD(MINUTE, -60, CURRENT_TIMESTAMP(3)) WHERE id = ?
                """, file.id());

        reconciliationRecoveryJob.recover();

        assertThat(reconciliationRepository.findFileById(file.id()).orElseThrow().status()).isEqualTo("FAILED");
    }

    private AdminReconciliationOperations.StatementView upload(String name, String... rows) {
        String header = "商户订单号,支付宝交易号,订单金额,交易状态,业务类型,创建时间\n";
        String body = header + String.join("\n", rows) + "\n";
        return reconciliationService.ingest("ALIPAY", LocalDate.now(), name, "text/csv",
                body.getBytes(StandardCharsets.UTF_8), fx.adminId());
    }

    private String row(String merchant, String provider, long cents, String status) {
        String time = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format(java.util.Locale.ROOT, "%s,%s,%.2f,%s,交易,%s",
                merchant, provider, cents / 100.0, status, time);
    }

    private PaymentAttemptRecord latestAttempt(String orderNo) {
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().id();
        return paymentRepository.findLatestAttempt(orderId).orElseThrow();
    }
}
