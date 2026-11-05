package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RateLimit;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/mock")
public class MockPaymentController {

    private final PaymentService paymentService;

    public MockPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{orderNo}")
    @RequireRole("STUDENT")
    @Measured("payment.mock.cashier")
    public PaymentDtos.MockCashierView cashier(@RequestAttribute("userId") Long userId,
                                               @PathVariable String orderNo) {
        return paymentService.cashier(userId, orderNo);
    }

    @PostMapping("/{orderNo}/events")
    @RequireRole("STUDENT")
    @RateLimit(key = "payment:mockEvent", limit = 30, windowSeconds = 60)
    @Measured("payment.mock.event")
    public PaymentDtos.MockEventResponse simulate(@RequestAttribute("userId") Long userId,
                                                  @PathVariable String orderNo,
                                                  @Valid @RequestBody PaymentDtos.MockEventRequest request) {
        return paymentService.simulate(userId, orderNo, request.scenario());
    }
}
