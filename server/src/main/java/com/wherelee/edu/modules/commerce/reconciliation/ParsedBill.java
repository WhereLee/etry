package com.wherelee.edu.modules.commerce.reconciliation;

import java.time.LocalDateTime;
import java.util.List;

public record ParsedBill(
        String parserVersion,
        String encoding,
        int rowCount,
        long totalAmountCents,
        List<Item> items
) {
    public record Item(
            int rowNo,
            String businessType,
            String merchantTradeNo,
            String providerTradeNo,
            String tradeStatus,
            String rawStatus,
            long amountCents,
            Long feeCents,
            String currency,
            String refundNo,
            LocalDateTime occurredAt,
            String rawDigest
    ) {
    }
}
