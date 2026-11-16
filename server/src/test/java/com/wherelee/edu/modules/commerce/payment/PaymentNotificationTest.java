package com.wherelee.edu.modules.commerce.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentNotificationTest {

    @Test
    void rejectsNullAttemptId() {
        assertThatThrownBy(() -> notification(null, "MOCK", "event-1", "ORD-1", "PAYMENT_SUCCESS"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("attemptId");
    }

    @Test
    void rejectsBlankProvider() {
        assertThatThrownBy(() -> notification(1L, " ", "event-1", "ORD-1", "PAYMENT_SUCCESS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void rejectsNullEventKey() {
        assertThatThrownBy(() -> notification(1L, "MOCK", null, "ORD-1", "PAYMENT_SUCCESS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventKey");
    }

    @Test
    void rejectsBlankOrderNo() {
        assertThatThrownBy(() -> notification(1L, "MOCK", "event-1", "", "PAYMENT_SUCCESS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderNo");
    }

    @Test
    void rejectsBlankEventType() {
        assertThatThrownBy(() -> notification(1L, "MOCK", "event-1", "ORD-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void acceptsValidNotificationAndAllowsOptionalNullFields() {
        PaymentNotification notification = notification(1L, "MOCK", "event-1", "ORD-1", "PAYMENT_SUCCESS");
        assertThat(notification.attemptId()).isEqualTo(1L);
        assertThat(notification.tradeNo()).isEqualTo("TRADE-1");
        assertThat(notification.providerNotifyId()).isNull();
        assertThat(notification.signature()).isNull();
    }

    private PaymentNotification notification(Long attemptId, String provider, String eventKey,
                                             String orderNo, String eventType) {
        return new PaymentNotification(provider, eventKey, attemptId, orderNo, eventType,
                100L, "TRADE-1", "MOCK-ORD-1-1", null, null, null);
    }
}
