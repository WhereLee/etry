package com.wherelee.edu.modules.commerce.order;

import com.wherelee.edu.modules.platform.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTransitionPolicyTest {

    @Test
    void customerCannotClosePaidOrPayingOrders() {
        assertThatThrownBy(() -> OrderTransitionPolicy.requireCustomerClosable(OrderStatus.PAID))
                .isInstanceOf(BizException.class)
                .hasMessage("已支付订单不能直接关闭");
        assertThatThrownBy(() -> OrderTransitionPolicy.requireCustomerClosable(OrderStatus.PAYING))
                .isInstanceOf(BizException.class)
                .hasMessage("支付中订单不能直接关闭，请先查询支付结果");
    }

    @Test
    void adminCannotClosePaidPayingOrRefundFlowOrders() {
        for (OrderStatus status : new OrderStatus[]{
                OrderStatus.PAID, OrderStatus.FULFILLED,
                OrderStatus.PAYING, OrderStatus.REFUNDING, OrderStatus.REFUNDED, OrderStatus.REFUND_FAILED
        }) {
            assertThatThrownBy(() -> OrderTransitionPolicy.requireAdminClosable(status))
                    .isInstanceOf(BizException.class);
        }
        OrderTransitionPolicy.requireAdminClosable(OrderStatus.PAY_FAILED);
        OrderTransitionPolicy.requireAdminClosable(OrderStatus.CREATED);
    }

    @Test
    void onlyPaidFulfilledOrFailedRefundCanBeRefunded() {
        for (OrderStatus status : new OrderStatus[]{
                OrderStatus.PAID, OrderStatus.FULFILLED, OrderStatus.REFUND_FAILED
        }) {
            OrderTransitionPolicy.requireRefundable(status);
        }
        assertThatThrownBy(() -> OrderTransitionPolicy.requireRefundable(OrderStatus.CREATED))
                .isInstanceOf(BizException.class)
                .hasMessage("当前订单状态不能退款");
    }

    @Test
    void onlyPaidOrdersCanBeFulfilled() {
        assertThat(OrderTransitionPolicy.isFulfillable(OrderStatus.PAID)).isTrue();
        assertThat(OrderTransitionPolicy.isFulfillable(OrderStatus.FULFILLED)).isFalse();
        assertThat(OrderTransitionPolicy.isFulfillable(OrderStatus.PAYING)).isFalse();
    }
}