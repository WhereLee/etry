package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.commerce.api.AdminReconciliationOperations;
import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.PaymentAttemptRecord;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.commerce.reconciliation.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationIntegrationTest extends CommerceIntegrationTestSupport {

    @Autowired private ReconciliationService reconciliationService;
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
