package com.wherelee.material.modules.commerce.outbox;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RabbitOutboxConfig {

    public static final String EXCHANGE = "btry.outbox";
    public static final String DEAD_EXCHANGE = "btry.outbox.dlx";
    public static final String ROUTING_FULFILL = "fulfill";
    public static final String ROUTING_REVOKE = "revoke";
    public static final String ROUTING_DEAD = "dead";
    public static final String FULFILL_QUEUE = "btry.outbox.fulfill";
    public static final String REVOKE_QUEUE = "btry.outbox.revoke";
    public static final String DEAD_QUEUE = "btry.outbox.dlq";

    @Bean
    TopicExchange outboxExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange outboxDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue fulfillQueue() {
        return QueueBuilder.durable(FULFILL_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DEAD)
                .build();
    }

    @Bean
    Queue revokeQueue() {
        return QueueBuilder.durable(REVOKE_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DEAD)
                .build();
    }

    @Bean
    Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding fulfillBinding(Queue fulfillQueue, TopicExchange outboxExchange) {
        return BindingBuilder.bind(fulfillQueue).to(outboxExchange).with(ROUTING_FULFILL);
    }

    @Bean
    Binding revokeBinding(Queue revokeQueue, TopicExchange outboxExchange) {
        return BindingBuilder.bind(revokeQueue).to(outboxExchange).with(ROUTING_REVOKE);
    }

    @Bean
    Binding deadBinding(Queue deadQueue, DirectExchange outboxDeadExchange) {
        return BindingBuilder.bind(deadQueue).to(outboxDeadExchange).with(ROUTING_DEAD);
    }

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
