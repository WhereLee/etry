package com.wherelee.edu.modules.commerce.payment;

public record PaymentNotification(
        String provider,
        String eventKey,
        String orderNo,
        String eventType,
        long amountCents,
        String tradeNo,
        String signature
) {
}
