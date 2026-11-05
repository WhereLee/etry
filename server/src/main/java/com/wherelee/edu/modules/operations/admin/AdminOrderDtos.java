package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.commerce.order.dto.OrderDtos;
import com.wherelee.edu.modules.commerce.payment.PaymentDtos;

import java.util.List;

public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    public record Detail(
            AdminOrderVO order,
            OrderDtos.OrderDetail orderDetail,
            List<PaymentDtos.AttemptView> attempts,
            List<PaymentDtos.EventView> events,
            PaymentDtos.RefundView refund
    ) {
    }
}
