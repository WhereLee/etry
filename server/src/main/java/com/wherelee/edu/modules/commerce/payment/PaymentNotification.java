package com.wherelee.edu.modules.commerce.payment;

import java.util.Objects;

/**
 * 渠道已验签的支付通知。
 *
 * <p>attemptId 必填：每笔事件必须明确关联一个支付尝试，迟到通知按事件自身的尝试处理，
 * 不允许回退到“最新尝试”。不提供省略 attemptId 的重载构造器。</p>
 *
 * @param attemptId        本地支付尝试 ID
 * @param tradeNo          渠道交易号（支付宝 trade_no）
 * @param providerTradeNo  商户侧支付单号（支付宝 out_trade_no，即 attempt.provider_trade_no）
 * @param providerNotifyId 渠道投递标识（支付宝 notify_id），仅用于排障，不作业务幂等键
 * @param payloadDigest    脱敏报文 SHA-256 摘要，用于审计
 */
public record PaymentNotification(
        String provider,
        String eventKey,
        Long attemptId,
        String orderNo,
        String eventType,
        long amountCents,
        String tradeNo,
        String providerTradeNo,
        String providerNotifyId,
        String payloadDigest,
        String signature
) {
    public PaymentNotification {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        requireNonBlank(provider, "provider");
        requireNonBlank(eventKey, "eventKey");
        requireNonBlank(orderNo, "orderNo");
        requireNonBlank(eventType, "eventType");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
