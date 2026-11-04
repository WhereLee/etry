package com.wherelee.edu.payment;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void orderCreated() { inc("payment.orders.submitted"); }
    public void paymentConfirmed() { inc("payment.order.paid"); }
    public void paymentFailed() { inc("payment.order.pay_failed"); }
    public void callbackDuplicate() { inc("payment.callback.duplicate"); }
    public void callbackInvalid() { inc("payment.callback.invalid"); }
    public void amountMismatch() { inc("payment.callback.amount_mismatch"); }
    public void fulfillmentFailed() { inc("payment.fulfillment.failed"); }
    public void fulfillmentSucceeded() { inc("payment.order.fulfilled"); }
    public void refundSucceeded() { inc("payment.refund.succeeded"); }
    public void refundFailed() { inc("payment.refund.failed"); }

    private void inc(String name) {
        registry.counter(name).increment();
    }
}
