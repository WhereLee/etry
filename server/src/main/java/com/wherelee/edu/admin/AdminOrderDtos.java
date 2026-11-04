package com.wherelee.edu.admin;

import com.wherelee.edu.order.dto.OrderDtos;
import com.wherelee.edu.payment.PaymentDtos;

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
