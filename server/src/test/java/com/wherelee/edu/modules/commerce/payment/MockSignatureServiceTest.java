package com.wherelee.edu.modules.commerce.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockSignatureServiceTest {

    private final MockSignatureService service = new MockSignatureService(
            new PaymentProperties("mock", true, "test-secret", 15, 15, 20, 15000, 10000, 5, 30)
    );

    @Test
    void signatureRejectsTamperedAmount() {
        PaymentNotification signed = signed(base(100L, 10L));

        assertThat(service.verify(signed)).isTrue();

        PaymentNotification tampered = new PaymentNotification(
                signed.provider(), signed.eventKey(), signed.attemptId(), signed.orderNo(),
                signed.eventType(), 999L, signed.tradeNo(), signed.providerTradeNo(),
                signed.providerNotifyId(), signed.payloadDigest(), signed.signature()
        );
        assertThat(service.verify(tampered)).isFalse();
    }

    @Test
    void signatureRejectsTamperedAttemptId() {
        PaymentNotification signed = signed(base(100L, 10L));

        assertThat(service.verify(signed)).isTrue();

        PaymentNotification tampered = new PaymentNotification(
                signed.provider(), signed.eventKey(), 20L, signed.orderNo(),
                signed.eventType(), signed.amountCents(), signed.tradeNo(), signed.providerTradeNo(),
                signed.providerNotifyId(), signed.payloadDigest(), signed.signature()
        );
        assertThat(service.verify(tampered)).isFalse();
    }

    private PaymentNotification base(long amount, Long attemptId) {
        return new PaymentNotification(
                "MOCK", "event-1", attemptId, "ORD-1", "PAYMENT_SUCCESS", amount,
                "TRADE-1", "MOCK-ORD-1-1", null, null, null
        );
    }

    private PaymentNotification signed(PaymentNotification original) {
        return new PaymentNotification(
                original.provider(), original.eventKey(), original.attemptId(), original.orderNo(),
                original.eventType(), original.amountCents(), original.tradeNo(), original.providerTradeNo(),
                original.providerNotifyId(), original.payloadDigest(), service.sign(original)
        );
    }
}
