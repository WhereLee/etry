package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConcurrencyIntegrationTest extends CommerceIntegrationTestSupport {

    @Test
    void concurrentPayCallsCreateOneProcessingAttempt() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());

        List<CallResult<OrderDtos.PayResponse>> results =
                runConcurrently(20, () -> paymentService.pay(fx.studentId(), order.orderNo()));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.stream().map(result -> result.value().cashierUrl()).distinct().toList()).hasSize(1);
        assertThat(count("t_payment_attempt", "order_id = ?", orderId(order.orderNo()))).isEqualTo(1);
        assertThat(count("t_payment_attempt", "order_id = ? AND status = 'PROCESSING'", orderId(order.orderNo())))
                .isEqualTo(1);
    }

    @Test
    void duplicateSuccessCallbacksCreateOneEventAndOneEntitlement() throws Exception {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        var notification = successNotification(order.orderNo());

        List<CallResult<com.wherelee.edu.modules.commerce.payment.PaymentConfirmationService.ConfirmationResult>> results =
                runConcurrently(20, () -> paymentService.acceptVerified(notification));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(count("t_payment_event", "order_no = ?", order.orderNo())).isEqualTo(1);
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);

        OrderRecord current = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(current.status()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void amountMismatchDoesNotFulfillOrder() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        pay(order.orderNo());
        var attempt = paymentRepository.findLatestAttempt(orderId(order.orderNo())).orElseThrow();

        var result = paymentService.acceptVerified(new com.wherelee.edu.modules.commerce.payment.PaymentNotification(
                "MOCK",
                "IT:" + order.orderNo() + ":MISMATCH",
                order.orderNo(),
                "PAYMENT_SUCCESS",
                fx.priceCents() + 100,
                attempt.providerTradeNo(),
                null
        ));

        assertThat(result.success()).isFalse();
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isZero();
        assertThat(count("t_payment_event", "order_no = ? AND process_status = 'FAILED'",
                order.orderNo())).isEqualTo(1);
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.PAYING);
    }

    @Test
    void failureAfterSuccessDoesNotRevertFulfilledOrder() {
        OrderDtos.OrderView order = createOrder(nextRequestId());
        completePayment(order.orderNo());
        var attempt = paymentRepository.findLatestAttempt(orderId(order.orderNo())).orElseThrow();

        var result = paymentService.acceptVerified(new com.wherelee.edu.modules.commerce.payment.PaymentNotification(
                "MOCK",
                "IT:" + order.orderNo() + ":LATE_FAILURE",
                order.orderNo(),
                "PAYMENT_FAILED",
                fx.priceCents(),
                attempt.providerTradeNo(),
                null
        ));

        assertThat(result.success()).isTrue();
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(count("t_student_series", "user_id = ? AND series_id = ? AND status = 'ACTIVE'",
                fx.studentId(), fx.seriesId())).isEqualTo(1);
    }

    private Long orderId(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).orElseThrow().id();
    }
}
