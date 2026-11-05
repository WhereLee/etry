package com.wherelee.edu.modules.commerce.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockSignatureServiceTest {

    private final MockSignatureService service = new MockSignatureService(
            new PaymentProperties("mock", true, "test-secret", 15, 15, 20, 15000, 10000, 5, 30)
    );

    @Test
    void signatureRejectsTamperedAmount() {
        PaymentNotification original = new PaymentNotification(
                "MOCK", "event-1", "ORD-1", "PAYMENT_SUCCESS", 100L, "TRADE-1", null
        );
        PaymentNotification signed = new PaymentNotification(
                original.provider(), original.eventKey(), original.orderNo(), original.eventType(),
                original.amountCents(), original.tradeNo(), service.sign(original)
        );

        assertThat(service.verify(signed)).isTrue();

        PaymentNotification tampered = new PaymentNotification(
                signed.provider(), signed.eventKey(), signed.orderNo(), signed.eventType(),
                999L, signed.tradeNo(), signed.signature()
        );
        assertThat(service.verify(tampered)).isFalse();
    }
}
