package com.wherelee.edu.modules.commerce.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyIntegrationTest extends CommerceIntegrationTestSupport {

    @Test
    void sameClientRequestCreatesExactlyOneOrder() throws Exception {
        List<CallResult<com.wherelee.edu.modules.commerce.order.dto.OrderDtos.OrderView>> results =
                runConcurrently(20, () -> createOrder("it-same-request"));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.stream().map(result -> result.value().orderNo()).distinct().toList()).hasSize(1);
        assertThat(count("t_order", null)).isEqualTo(1);
        assertThat(count("t_student_series", null)).isZero();
    }

    @Test
    void differentClientRequestsStillCreateOneActiveOrder() throws Exception {
        List<CallResult<com.wherelee.edu.modules.commerce.order.dto.OrderDtos.OrderView>> results =
                runConcurrently(20, () -> createOrder(nextRequestId()));

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.stream().map(result -> result.value().orderNo()).distinct().toList()).hasSize(1);
        assertThat(count("t_order", null)).isEqualTo(1);
    }
}
