package com.wherelee.drill;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RocketMqDrill {

    private static final int ORDERED_COUNT = 20;

    private RocketMqDrill() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("rocketmq.client.logLevel", "ERROR");
        String namesrv = System.getenv().getOrDefault("NAMESRV_ADDR", "127.0.0.1:9876");
        String suffix = String.valueOf(System.currentTimeMillis());
        String topic = "btry-drill-topic-" + suffix;
        String consumerGroup = "btry_drill_consumer_" + suffix;

        CountDownLatch received = new CountDownLatch(ORDERED_COUNT + 1);
        AtomicInteger ordered = new AtomicInteger();
        AtomicInteger delayed = new AtomicInteger();
        AtomicInteger outOfOrder = new AtomicInteger();
        long[] delayedSentAt = new long[1];

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(namesrv);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerOrderly) (messages, context) -> {
            for (MessageExt message : messages) {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                if ("delayed".equals(message.getTags())) {
                    delayed.set((int) (System.currentTimeMillis() - delayedSentAt[0]));
                } else {
                    ordered.incrementAndGet();
                    if (!body.equals("order-" + ordered.get())) {
                        outOfOrder.incrementAndGet();
                    }
                }
                received.countDown();
            }
            return ConsumeOrderlyStatus.SUCCESS;
        });
        consumer.start();

        DefaultMQProducer producer = new DefaultMQProducer("btry_drill_producer_" + suffix);
        producer.setNamesrvAddr(namesrv);
        producer.start();

        for (int i = 1; i <= ORDERED_COUNT; i++) {
            Message message = new Message(topic, "fulfill", ("order-" + i).getBytes(StandardCharsets.UTF_8));
            message.setKeys("order-" + i);
            producer.send(message, (queues, msg, arg) -> queues.get(0), i);
        }

        Message delayedMessage = new Message(topic, "delayed", "delayed".getBytes(StandardCharsets.UTF_8));
        delayedMessage.setDelayTimeLevel(2);
        delayedSentAt[0] = System.currentTimeMillis();
        producer.send(delayedMessage);

        boolean completed = received.await(40, TimeUnit.SECONDS);
        boolean passed = completed && ordered.get() == ORDERED_COUNT && outOfOrder.get() == 0
                && delayed.get() >= 4000;
        String result = "topic=" + topic
                + "\nconsumer_group=" + consumerGroup
                + "\nordered_received=" + ordered.get()
                + "\nordered_out_of_order=" + outOfOrder.get()
                + "\ndelayed_elapsed_ms=" + delayed.get()
                + "\ncompleted=" + completed
                + "\npassed=" + passed;
        Files.writeString(Path.of("target", "drill-result.txt"), result, StandardCharsets.UTF_8);
        System.out.println(result);

        consumer.shutdown();
        producer.shutdown();
        System.exit(passed ? 0 : 1);
    }
}
