package com.wherelee.material.modules.commerce.outbox;

public record OutboxEvent(Long id, String messageType, String orderNo, String reason) {
}
