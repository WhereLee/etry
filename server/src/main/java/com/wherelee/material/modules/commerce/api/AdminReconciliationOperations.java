package com.wherelee.material.modules.commerce.api;

import com.wherelee.material.modules.platform.common.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营端渠道账单对账门面。账单解析、匹配和差异状态都封装在 commerce 内。
 */
public interface AdminReconciliationOperations {

    PageResult<StatementView> statements(int page, int size);

    StatementView ingest(String provider, LocalDate statementDate, String fileName,
                         String contentType, byte[] content, Long operatorId);

    List<RunView> runs(long fileId);

    RunView run(long fileId, Long operatorId);

    PageResult<BreakView> breaks(long runId, String status, int page, int size);

    BreakView actOnBreak(long breakId, String action, String resolutionCode,
                         String note, Long operatorId);

    record StatementView(
            Long id,
            String provider,
            LocalDate statementDate,
            String fileName,
            long fileSize,
            int rowCount,
            long totalAmountCents,
            String status,
            String parseError,
            Long importedBy,
            LocalDateTime importedAt,
            Long latestRunId,
            String latestRunStatus
    ) {
    }

    record RunView(
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
            LocalDateTime finishedAt
    ) {
    }

    record BreakView(
            Long id,
            Long runId,
            Long itemId,
            String breakType,
            String severity,
            String status,
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
            LocalDateTime createdAt
    ) {
    }
}
