package com.wherelee.material.modules.commerce.outbox;

import com.wherelee.material.modules.commerce.payment.OrderFulfillmentService;
import com.wherelee.material.modules.platform.alert.AlertPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 幂等消费者：履约/撤销本身幂等；异常重试耗尽后由队列 DLX 转入死信队列，
 * 死信消费者落告警便于人工处理。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
class OutboxRabbitConsumer {

    private final OrderFulfillmentService fulfillmentService;
    private final AlertPublisher alertPublisher;

    OutboxRabbitConsumer(OrderFulfillmentService fulfillmentService, AlertPublisher alertPublisher) {
        this.fulfillmentService = fulfillmentService;
        this.alertPublisher = alertPublisher;
    }

    @RabbitListener(queues = RabbitOutboxConfig.FULFILL_QUEUE)
    public void onFulfill(OutboxEvent event) {
        if (!fulfillmentService.fulfill(event.orderNo())) {
            throw new IllegalStateException("履约未完成 orderNo=" + event.orderNo());
        }
    }

    @RabbitListener(queues = RabbitOutboxConfig.REVOKE_QUEUE)
    public void onRevoke(OutboxEvent event) {
        if (!fulfillmentService.revoke(event.orderNo(), event.reason())) {
            throw new IllegalStateException("权益撤销未完成 orderNo=" + event.orderNo());
        }
    }

    @RabbitListener(queues = RabbitOutboxConfig.DEAD_QUEUE)
    public void onDead(OutboxEvent event) {
        String message = "outbox MQ 死信 orderNo=" + event.orderNo()
                + " type=" + event.messageType() + " outboxId=" + event.id();
        log.error(message);
        alertPublisher.publish("OUTBOX_MQ_DEAD", "ERROR", message, false, "dead letter queue");
    }
}
