package com.wherelee.material.modules.commerce.outbox;

/**
 * 发件箱投递传输。direct 在应用内直接履行；rabbit 发布到 MQ 后由消费者幂等履行。
 */
public interface OutboxTransport {

    boolean deliver(OutboxMessage message);
}
