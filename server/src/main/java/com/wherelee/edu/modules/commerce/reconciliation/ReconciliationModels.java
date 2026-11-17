package com.wherelee.edu.modules.commerce.reconciliation;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ReconciliationModels {

    private ReconciliationModels() {
    }

    public record StatementFile(
            Long id,
            String provider,
            LocalDate statementDate,
            String fileName,
            String contentType,
            String fileSha256,
            long fileSize,
            String parserVersion,
            int rowCount,
            long totalAmountCents,
            String status,
            String parseError,
            Long importedBy,
            LocalDateTime createdAt
    ) {
    }

    public record StatementRow(
            StatementFile file,
            Long latestRunId,
            String latestRunStatus
    ) {
    }

    public record StatementItem(
            Long id,
            Long fileId,
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

    public record Run(
            Long id,
            Long fileId,
            int runVersion,
            String status,
            String ruleVersion,
            int totalRows,
            int matchedCount,
            int breakCount,
            int unresolvedBreakCount,
            long unmatchedAmountCents,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long createdBy,
            LocalDateTime createdAt
    ) {
    }

    public record LocalPayment(
            Long attemptId,
            Long orderId,
            String orderNo,
            String merchantTradeNo,
            String channelTradeNo,
            String attemptStatus,
            long providerAmountCents,
            String orderStatus,
            long orderPriceCents,
            LocalDateTime paidAt
    ) {
    }

    public record LocalRefund(
            Long id,
            Long orderId,
            String refundNo,
            long amountCents,
            String status,
            String refundKind,
            String providerRefundNo,
            LocalDateTime succeededAt
    ) {
    }

    public record Break(
            Long id,
            Long runId,
            Long itemId,
            String breakType,
            String severity,
            String status,
            Long orderId,
            String orderNo,
            Long attemptId,
            String providerTradeNo,
            String merchantTradeNo,
            Long localAmountCents,
            Long providerAmountCents,
            long deltaCents,
            String detail,
            String resolutionCode,
            String resolutionNote,
            Long resolvedBy,
            LocalDateTime resolvedAt,
            int version,
            LocalDateTime createdAt
    ) {
    }
}
