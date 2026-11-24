package com.wherelee.material.modules.commerce.outbox;

import com.wherelee.material.modules.commerce.payment.OrderFulfillmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认传输：应用内直接履约/撤销。保持无 MQ 环境的既有行为。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
class DirectOutboxTransport implements OutboxTransport {

    private final OrderFulfillmentService fulfillmentService;

    DirectOutboxTransport(OrderFulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @Override
    public boolean deliver(OutboxMessage message) {
        return switch (message.messageType()) {
            case OutboxMessage.TYPE_FULFILL -> fulfillmentService.fulfill(message.orderNo());
            case OutboxMessage.TYPE_REVOKE -> fulfillmentService.revoke(message.orderNo(), message.reason());
            default -> false;
        };
    }
}
