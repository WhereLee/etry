package com.wherelee.material.modules.commerce.payment;

import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.aop.RateLimit;
import com.wherelee.material.modules.platform.aop.RequireRole;
import com.wherelee.material.modules.commerce.api.OrderDtos;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderPaymentController {

    private final PaymentService paymentService;

    public OrderPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/orders/{orderNo}/pay")
    @RequireRole("STUDENT")
    @RateLimit(key = "order:pay", limit = 20, windowSeconds = 60)
    @Measured("order.pay")
    public OrderDtos.PayResponse pay(@RequestAttribute("userId") Long userId,
                                     @PathVariable String orderNo) {
        return paymentService.pay(userId, orderNo);
    }
}
