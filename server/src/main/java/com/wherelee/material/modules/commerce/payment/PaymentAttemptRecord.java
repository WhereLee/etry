package com.wherelee.material.modules.commerce.payment;

import java.time.LocalDateTime;

public record PaymentAttemptRecord(
        Long id,
        Long orderId,
        int attemptNo,
        String channel,
        String status,
        // 历史命名：实际存的是商户 out_trade_no，不是渠道 trade_no。
        String providerTradeNo,
        // 渠道真实交易号，例如支付宝 trade_no；由成功通知或主动查单写入。
        String channelTradeNo,
        String providerStatus,
        long providerAmountCents,
        String requestDigest,
        String failureCode,
        String failureMessage,
        String mockScenario,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
