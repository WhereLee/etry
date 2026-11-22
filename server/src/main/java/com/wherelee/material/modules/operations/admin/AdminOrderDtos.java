package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.api.PaymentDtos;

import java.util.List;

public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    public record Detail(
            AdminOrderVO order,
            OrderDtos.OrderDetail orderDetail,
            List<PaymentDtos.AttemptView> attempts,
            List<PaymentDtos.EventView> events,
            List<PaymentDtos.RefundView> refunds
    ) {
    }
}
