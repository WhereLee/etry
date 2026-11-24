package com.wherelee.material.modules.commerce.outbox;

import com.wherelee.material.modules.commerce.payment.OrderFulfillmentService;
import com.wherelee.material.modules.platform.alert.AlertPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRabbitConsumerTest {

    @Mock private OrderFulfillmentService fulfillmentService;
    @Mock private AlertPublisher alertPublisher;

    private OutboxRabbitConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OutboxRabbitConsumer(fulfillmentService, alertPublisher);
    }

    @Test
    void fulfillFailureThrowsForRetryAndDeadLetter() {
        OutboxEvent event = new OutboxEvent(1L, "FULFILL", "ORD-1", null);
        when(fulfillmentService.fulfill("ORD-1")).thenReturn(false);

        assertThatThrownBy(() -> consumer.onFulfill(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORD-1");
    }

    @Test
    void fulfillSuccessAcknowledges() {
        OutboxEvent event = new OutboxEvent(1L, "FULFILL", "ORD-1", null);
        when(fulfillmentService.fulfill("ORD-1")).thenReturn(true);

        consumer.onFulfill(event);

        verify(fulfillmentService).fulfill("ORD-1");
    }

    @Test
    void revokeFailureThrowsForRetryAndDeadLetter() {
        OutboxEvent event = new OutboxEvent(2L, "REVOKE", "ORD-2", "退款");
        when(fulfillmentService.revoke("ORD-2", "退款")).thenReturn(false);

        assertThatThrownBy(() -> consumer.onRevoke(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORD-2");
    }

    @Test
    void deadLetterPublishesAlert() {
        OutboxEvent event = new OutboxEvent(3L, "FULFILL", "ORD-3", null);

        consumer.onDead(event);

        verify(alertPublisher).publish(
                org.mockito.ArgumentMatchers.eq("OUTBOX_MQ_DEAD"),
                org.mockito.ArgumentMatchers.eq("ERROR"),
                org.mockito.ArgumentMatchers.contains("ORD-3"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.anyString());
    }
}
