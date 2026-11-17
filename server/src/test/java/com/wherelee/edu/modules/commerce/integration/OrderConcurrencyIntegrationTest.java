package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.integration.IntegrationTestSupport;

import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.api.OrderDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Test
    void sameClientRequestCreatesExactlyOneOrder() throws Exception {
        List<CallResult<com.wherelee.edu.modules.commerce.api.OrderDtos.OrderView>> results =
                runConcurrently(20, () -> createOrder("it-same-request"));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.stream().map(result -> result.value().orderNo()).distinct().toList()).hasSize(1);
        assertThat(count("t_order", null)).isEqualTo(1);
        assertThat(count("t_entitlement", null)).isZero();
    }

    @Test
    void genericProductRequestCreatesOrderWithSeriesAlias() {
        OrderDtos.OrderView order = orderService.create(fx.studentId(),
                new OrderDtos.CreateOrderRequest(null, fx.seriesId(), "COURSE_SERIES", nextRequestId()));

        assertThat(order.productId()).isEqualTo(fx.seriesId());
        assertThat(order.productType()).isEqualTo("COURSE_SERIES");
        assertThat(order.seriesId()).isEqualTo(fx.seriesId());
        assertThat(order.productTitle()).isEqualTo(order.seriesTitle());

        completePayment(order.orderNo());
        assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void differentClientRequestsStillCreateOneActiveOrder() throws Exception {
        List<CallResult<com.wherelee.edu.modules.commerce.api.OrderDtos.OrderView>> results =
                runConcurrently(20, () -> createOrder(nextRequestId()));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.stream().map(result -> result.value().orderNo()).distinct().toList()).hasSize(1);
        assertThat(count("t_order", null)).isEqualTo(1);
    }
}
