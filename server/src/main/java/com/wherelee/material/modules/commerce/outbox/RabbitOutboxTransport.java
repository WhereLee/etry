package com.wherelee.material.modules.commerce.outbox;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 传输：发布确认（publisher confirm）成功后才允许 outbox 标记 DONE；
 * 消费者幂等履行，失败重试后进入 DLQ。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
class RabbitOutboxTransport implements OutboxTransport {

    private final RabbitTemplate rabbitTemplate;

    RabbitOutboxTransport(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public boolean deliver(OutboxMessage message) {
        String routingKey = switch (message.messageType()) {
            case OutboxMessage.TYPE_FULFILL -> RabbitOutboxConfig.ROUTING_FULFILL;
            case OutboxMessage.TYPE_REVOKE -> RabbitOutboxConfig.ROUTING_REVOKE;
            default -> null;
        };
        if (routingKey == null) {
            return false;
        }
        try {
            CorrelationData correlation = new CorrelationData(String.valueOf(message.id()));
            rabbitTemplate.convertAndSend(
                    RabbitOutboxConfig.EXCHANGE, routingKey,
                    new OutboxEvent(message.id(), message.messageType(), message.orderNo(), message.reason()),
                    correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            return confirm != null && confirm.isAck();
        } catch (Exception e) {
            return false;
        }
    }
}
