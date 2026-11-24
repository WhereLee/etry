package com.wherelee.material.modules.commerce.reconciliation;

import com.wherelee.material.modules.commerce.api.AdminReconciliationOperations;
import com.wherelee.material.modules.commerce.payment.PaymentRepository;
import com.wherelee.material.modules.commerce.payment.RefundRecord;
import com.wherelee.material.modules.platform.common.BizException;
import com.wherelee.material.modules.platform.common.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

@Service
public class ReconciliationService implements AdminReconciliationOperations {

    private static final String RULE_VERSION = "PAYMENT_STATEMENT_V1";

    private final ReconciliationRepository repository;
    private final AlipayBillParser parser;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;

    public ReconciliationService(ReconciliationRepository repository,
                                 AlipayBillParser parser,
                                 PaymentRepository paymentRepository,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.parser = parser;
        this.paymentRepository = paymentRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public PageResult<StatementView> statements(int page, int size) {
        PageResult<ReconciliationModels.StatementRow> result = repository.findStatements(page, size);
        List<StatementView> rows = result.records().stream().map(this::statementView).toList();
        return new PageResult<>(rows, result.total(), result.page(), result.size());
    }

    @Override
    public StatementView ingest(String provider, LocalDate statementDate, String fileName,
                                String contentType, byte[] content, Long operatorId) {
        String normalizedProvider = provider == null ? "ALIPAY" : provider.trim().toUpperCase(Locale.ROOT);
        if (!"ALIPAY".equals(normalizedProvider)) {
            throw BizException.badRequest("当前仅支持支付宝账单");
        }
        if (statementDate == null) {
            throw BizException.badRequest("请选择账单日期");
        }
        if (content == null || content.length == 0) {
            throw BizException.badRequest("账单文件为空");
        }
        if (content.length > 20 * 1024 * 1024) {
            throw BizException.badRequest("账单文件不能超过 20MB");
        }
        String sha = sha256(content);
        Optional<ReconciliationModels.StatementFile> existing =
                repository.findFileByDigest(normalizedProvider, statementDate, sha);
        if (existing.isPresent()) {
            if ("PARSED".equals(existing.get().status())) {
                return statementView(existing.get(), null, null);
            }
            if ("PARSING".equals(existing.get().status())) {
                throw new BizException(HttpStatus.CONFLICT, "同名账单正在解析，请稍后刷新");
            }
            StatementView reparsed = transactionTemplate.execute(status -> {
                repository.reopenFile(existing.get().id());
                return parseAndStore(existing.get().id(), content);
            });
            if (reparsed == null) {
                throw new IllegalStateException("账单重新解析后不可读取");
            }
            return reparsed;
        }

        try {
            StatementView parsed = transactionTemplate.execute(status -> {
                Long fileId = repository.createFile(normalizedProvider, statementDate,
                        safeFileName(fileName), contentType, sha, content.length,
                        gzip(content), AlipayBillParser.PARSER_VERSION, operatorId);
                return parseAndStore(fileId, content);
            });
            if (parsed == null) {
                throw new IllegalStateException("账单解析后不可读取");
            }
            return parsed;
        } catch (DuplicateKeyException e) {
            ReconciliationModels.StatementFile file = repository.findFileByDigest(
                            normalizedProvider, statementDate, sha)
                    .orElseThrow(() -> new IllegalStateException("并发上传账单后不可读取"));
            if ("PARSING".equals(file.status())) {
                throw new BizException(HttpStatus.CONFLICT, "同名账单正在解析，请稍后刷新");
            }
            return statementView(file, null, null);
        }
    }

    private StatementView parseAndStore(Long fileId, byte[] content) {
        try {
            ParsedBill bill = parser.parse(content);
            transactionTemplate.executeWithoutResult(status -> {
                repository.insertItems(fileId, bill.items());
                repository.markFileParsed(fileId, bill.rowCount(), bill.totalAmountCents(), bill.parserVersion());
            });
        } catch (Exception e) {
            repository.markFileFailed(fileId, e.getMessage());
            throw BizException.badRequest("账单解析失败：" + e.getMessage());
        }
        ReconciliationModels.StatementFile file = repository.findFileById(fileId)
                .orElseThrow(() -> new IllegalStateException("账单解析后不可读取"));
        return statementView(file, null, null);
    }

    @Override
    public List<RunView> runs(long fileId) {
        return repository.findRunsByFile(fileId).stream().map(this::runView).toList();
    }

    @Override
    public RunView run(long fileId, Long operatorId) {
        try {
            RunView view = transactionTemplate.execute(status -> {
                ReconciliationModels.StatementFile file = repository.lockFileForRun(fileId)
                        .orElseThrow(() -> BizException.notFound("账单不存在"));
                if (!"PARSED".equals(file.status())) {
                    throw BizException.badRequest("只有解析成功的账单才能执行对账");
                }
                int version = repository.nextRunVersion(fileId);
                Long runId = repository.createRun(fileId, version, RULE_VERSION, operatorId);
                MatchingResult result = match(file, runId);
                if (result == null) {
                    throw new IllegalStateException("对账结果为空");
                }
                repository.completeRun(runId, result.totalRows(), result.matched(),
                        result.breaks(), result.unresolved(), result.unmatchedAmount());
                return runView(repository.findRunById(runId)
                        .orElseThrow(() -> new IllegalStateException("对账运行后不可读取")));
            });
            if (view == null) {
                throw new IllegalStateException("对账结果为空");
            }
            return view;
        } catch (BizException e) {
            throw e;
        } catch (DuplicateKeyException e) {
            throw new BizException(HttpStatus.CONFLICT, "该账单已有对账任务正在运行");
        } catch (Exception e) {
            throw new BizException(HttpStatus.CONFLICT, "对账执行失败：" + e.getMessage());
        }
    }

    @Override
    public PageResult<BreakView> breaks(long runId, String status, int page, int size) {
        PageResult<ReconciliationModels.Break> result = repository.findBreaks(runId, status, page, size);
        return new PageResult<>(result.records().stream().map(this::breakView).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public BreakView actOnBreak(long breakId, String action, String resolutionCode,
                                String note, Long operatorId) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CLAIM", "RESOLVE", "IGNORE").contains(normalized)) {
            throw BizException.badRequest("不支持的对账处理动作");
        }
        if ("RESOLVE".equals(normalized) && (resolutionCode == null || resolutionCode.isBlank())) {
            throw BizException.badRequest("处理差异时必须填写处理代码");
        }
        if (!"CLAIM".equals(normalized) && (note == null || note.isBlank())) {
            throw BizException.badRequest("请填写处理说明");
        }
        ReconciliationModels.Break updated = transactionTemplate.execute(status -> {
            ReconciliationModels.Break current = repository.findBreakByIdForUpdate(breakId)
                    .orElseThrow(() -> BizException.notFound("对账差异不存在"));
            if (!Set.of("OPEN", "IN_REVIEW").contains(current.status())) {
                throw new BizException(HttpStatus.CONFLICT, "该差异已经处理完成");
            }
            String target = switch (normalized) {
                case "CLAIM" -> "IN_REVIEW";
                case "RESOLVE" -> "RESOLVED";
                default -> "IGNORED";
            };
            String code = "CLAIM".equals(normalized) ? current.resolutionCode() : resolutionCode;
            int changed = repository.updateBreakStatus(breakId, current.version(), target,
                    code, note, operatorId);
            if (changed != 1) {
                throw new BizException(HttpStatus.CONFLICT, "差异状态已变化，请刷新后重试");
            }
            repository.insertAction(breakId, normalized, current.status(), target, operatorId, note);
            repository.refreshRunUnresolvedCount(current.runId());
            return repository.findBreakByIdForUpdate(breakId)
                    .orElseThrow(() -> new IllegalStateException("差异处理后不可读取"));
        });
        if (updated == null) {
            throw new IllegalStateException("差异处理结果为空");
        }
        return breakView(updated);
    }

    private MatchingResult match(ReconciliationModels.StatementFile file, Long runId) {
        List<ReconciliationModels.StatementItem> items = repository.findItems(file.id());
        List<ReconciliationModels.LocalPayment> locals = repository.findLocalPayments(file.id(), file.statementDate());
        Map<String, ReconciliationModels.LocalPayment> localByMerchant = new LinkedHashMap<>();
        Map<Long, List<ReconciliationModels.LocalPayment>> localsByOrder = new LinkedHashMap<>();
        for (ReconciliationModels.LocalPayment local : locals) {
            if (local.merchantTradeNo() != null) {
                localByMerchant.put(local.merchantTradeNo(), local);
            }
            localsByOrder.computeIfAbsent(local.orderId(), key -> new ArrayList<>()).add(local);
        }

        Set<String> matchedMerchantTradeNos = new HashSet<>();
        Set<String> paymentStatementMerchants = new HashSet<>();
        for (ReconciliationModels.StatementItem item : items) {
            if ("PAYMENT".equals(item.businessType()) && item.merchantTradeNo() != null) {
                paymentStatementMerchants.add(item.merchantTradeNo());
            }
        }
        Set<String> seenProviderTradeNos = new HashSet<>();
        Map<Long, List<ReconciliationModels.StatementItem>> matchedByOrder = new LinkedHashMap<>();
        int matched = 0;
        int breaks = 0;
        long unmatchedAmount = 0;

        for (ReconciliationModels.StatementItem item : items) {
            if ("PAYMENT".equals(item.businessType())) {
                if (!"SUCCESS".equals(item.tradeStatus())) {
                    ReconciliationModels.LocalPayment local = localByMerchant.get(item.merchantTradeNo());
                    if (local != null && "SUCCESS".equals(local.attemptStatus())) {
                        repository.insertBreak(runId, item.id(), "STATUS_MISMATCH", "CRITICAL",
                                local.orderId(), local.orderNo(), local.attemptId(),
                                item.providerTradeNo(), item.merchantTradeNo(),
                                local.orderPriceCents(), item.amountCents(), 0,
                                "渠道账单状态为 " + item.rawStatus() + "，本地却已支付成功");
                        breaks++;
                        unmatchedAmount += item.amountCents();
                    }
                    continue;
                }
                if (!seenProviderTradeNos.add(item.providerTradeNo())) {
                    repository.insertBreak(runId, item.id(), "DUPLICATE_PROVIDER_TRADE", "HIGH",
                            null, null, null, item.providerTradeNo(), item.merchantTradeNo(),
                            null, item.amountCents(), item.amountCents(),
                            "同一账单中支付宝交易号重复");
                    breaks++;
                    unmatchedAmount += item.amountCents();
                    continue;
                }
                ReconciliationModels.LocalPayment local = localByMerchant.get(item.merchantTradeNo());
                if (local == null) {
                    repository.insertBreak(runId, item.id(), "PROVIDER_ONLY", "CRITICAL",
                            null, null, null, item.providerTradeNo(), item.merchantTradeNo(),
                            null, item.amountCents(), item.amountCents(),
                            "渠道有成功流水，本地找不到对应支付尝试");
                    breaks++;
                    unmatchedAmount += item.amountCents();
                    continue;
                }
                if (item.amountCents() != local.orderPriceCents()) {
                    long delta = item.amountCents() - local.orderPriceCents();
                    repository.insertBreak(runId, item.id(), "AMOUNT_MISMATCH", "CRITICAL",
                            local.orderId(), local.orderNo(), local.attemptId(),
                            item.providerTradeNo(), item.merchantTradeNo(),
                            local.orderPriceCents(), item.amountCents(), delta,
                            "渠道金额与本地订单金额不一致");
                    breaks++;
                    unmatchedAmount += Math.abs(delta);
                    continue;
                }
                if (local.channelTradeNo() == null || local.channelTradeNo().isBlank()
                        || !local.channelTradeNo().equals(item.providerTradeNo())) {
                    repository.insertBreak(runId, item.id(), "TRADE_NO_MISMATCH", "HIGH",
                            local.orderId(), local.orderNo(), local.attemptId(),
                            item.providerTradeNo(), item.merchantTradeNo(),
                            local.orderPriceCents(), item.amountCents(), 0,
                            "本地渠道交易号缺失或与账单不一致 localTradeNo=" + local.channelTradeNo());
                    breaks++;
                    unmatchedAmount += item.amountCents();
                    continue;
                }
                if (!"SUCCESS".equals(local.attemptStatus())) {
                    repository.insertBreak(runId, item.id(), "STATUS_MISMATCH", "CRITICAL",
                            local.orderId(), local.orderNo(), local.attemptId(),
                            item.providerTradeNo(), item.merchantTradeNo(),
                            local.orderPriceCents(), item.amountCents(), 0,
                            "渠道已成功，本地支付尝试状态为 " + local.attemptStatus());
                    breaks++;
                    unmatchedAmount += item.amountCents();
                    continue;
                }
                repository.insertMatch(runId, item.id(), local.orderId(), local.orderNo(),
                        local.attemptId(), "EXACT");
                matchedMerchantTradeNos.add(item.merchantTradeNo());
                matchedByOrder.computeIfAbsent(local.orderId(), key -> new ArrayList<>()).add(item);
                matched++;
                continue;
            }

            if ("REFUND".equals(item.businessType()) && "SUCCESS".equals(item.tradeStatus())) {
                ReconciliationModels.LocalPayment local = localByMerchant.get(item.merchantTradeNo());
                List<RefundRecord> refunds = local == null ? List.of()
                        : paymentRepository.findRefundsByOrder(local.orderId());
                RefundRecord refund = matchRefund(item, refunds);
                if (local == null || refund == null) {
                    repository.insertBreak(runId, item.id(), "REFUND_MISMATCH", "HIGH",
                            local == null ? null : local.orderId(),
                            local == null ? null : local.orderNo(),
                            local == null ? null : local.attemptId(),
                            item.providerTradeNo(), item.merchantTradeNo(),
                            local == null ? null : local.orderPriceCents(), item.amountCents(), 0,
                            "渠道退款流水无法唯一匹配本地退款");
                    breaks++;
                    unmatchedAmount += item.amountCents();
                    continue;
                }
                repository.insertMatch(runId, item.id(), local.orderId(), local.orderNo(),
                        local.attemptId(), "REFUND_EXACT");
                matched++;
            }
        }

        for (ReconciliationModels.LocalPayment local : locals) {
            if (!"SUCCESS".equals(local.attemptStatus()) || local.paidAt() == null
                    || !local.paidAt().toLocalDate().equals(file.statementDate())
                    || paymentStatementMerchants.contains(local.merchantTradeNo())
                    || matchedMerchantTradeNos.contains(local.merchantTradeNo())) {
                continue;
            }
            repository.insertBreak(runId, null, "LOCAL_ONLY", "CRITICAL",
                    local.orderId(), local.orderNo(), local.attemptId(),
                    local.channelTradeNo(), local.merchantTradeNo(),
                    local.orderPriceCents(), null, local.orderPriceCents(),
                    "本地有成功支付尝试，渠道账单缺少对应流水");
            breaks++;
            unmatchedAmount += local.orderPriceCents();
        }

        for (Map.Entry<Long, List<ReconciliationModels.StatementItem>> entry : matchedByOrder.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            ReconciliationModels.LocalPayment local = localsByOrder.get(entry.getKey()).stream().findFirst().orElse(null);
            repository.insertBreak(runId, null, "DUPLICATE_PAYMENT", "CRITICAL",
                    entry.getKey(), local == null ? null : local.orderNo(), local == null ? null : local.attemptId(),
                    entry.getValue().get(0).providerTradeNo(), entry.getValue().get(0).merchantTradeNo(),
                    local == null ? null : local.orderPriceCents(), null, 0,
                    "同一订单出现多笔渠道成功付款，需按支付尝试退款");
            breaks++;
            unmatchedAmount += local == null ? 0 : local.orderPriceCents();
        }
        for (Map.Entry<Long, List<ReconciliationModels.LocalPayment>> entry : localsByOrder.entrySet()) {
            long successCount = entry.getValue().stream()
                    .filter(local -> "SUCCESS".equals(local.attemptStatus())).count();
            if (successCount <= 1 || matchedByOrder.getOrDefault(entry.getKey(), List.of()).size() > 1) {
                continue;
            }
            ReconciliationModels.LocalPayment local = entry.getValue().get(0);
            repository.insertBreak(runId, null, "DUPLICATE_PAYMENT", "CRITICAL",
                    local.orderId(), local.orderNo(), local.attemptId(),
                    local.channelTradeNo(), local.merchantTradeNo(),
                    local.orderPriceCents(), null, 0,
                    "同一订单本地存在多笔成功支付尝试，渠道账单未全部出现");
            breaks++;
            unmatchedAmount += local.orderPriceCents();
        }

        int unresolved = (int) repository.countUnresolvedBreaks(runId);
        return new MatchingResult(items.size(), matched, breaks, unresolved, unmatchedAmount);
    }

    private RefundRecord matchRefund(ReconciliationModels.StatementItem item, List<RefundRecord> refunds) {
        List<RefundRecord> succeeded = refunds.stream()
                .filter(refund -> "SUCCEEDED".equals(refund.status())).toList();
        if (item.refundNo() != null && !item.refundNo().isBlank()) {
            return succeeded.stream()
                    .filter(refund -> item.refundNo().equals(refund.refundNo())
                            || item.refundNo().equals(refund.providerRefundNo()))
                    .findFirst().orElse(null);
        }
        List<RefundRecord> amountMatches = succeeded.stream()
                .filter(refund -> refund.amountCents() == item.amountCents()).toList();
        return amountMatches.size() == 1 ? amountMatches.get(0) : null;
    }

    private StatementView statementView(ReconciliationModels.StatementRow row) {
        return statementView(row.file(), row.latestRunId(), row.latestRunStatus());
    }

    private StatementView statementView(ReconciliationModels.StatementFile file,
                                        Long latestRunId, String latestRunStatus) {
        return new StatementView(file.id(), file.provider(), file.statementDate(), file.fileName(),
                file.fileSize(), file.rowCount(), file.totalAmountCents(), file.status(),
                file.parseError(), file.importedBy(), file.createdAt(), latestRunId, latestRunStatus);
    }

    private RunView runView(ReconciliationModels.Run run) {
        return new RunView(run.id(), run.fileId(), run.runVersion(), run.status(), run.ruleVersion(),
                run.totalRows(), run.matchedCount(), run.breakCount(), run.unresolvedBreakCount(),
                run.unmatchedAmountCents(), run.errorMessage(), run.startedAt(), run.finishedAt());
    }

    private BreakView breakView(ReconciliationModels.Break item) {
        return new BreakView(item.id(), item.runId(), item.itemId(), item.breakType(),
                item.severity(), item.status(), item.orderNo(), item.attemptId(),
                item.providerTradeNo(), item.merchantTradeNo(), item.localAmountCents(),
                item.providerAmountCents(), item.deltaCents(), item.detail(),
                item.resolutionCode(), item.resolutionNote(), item.resolvedBy(),
                item.resolvedAt(), item.createdAt());
    }

    private String safeFileName(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "statement.csv" : fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private byte[] gzip(byte[] content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(content);
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("账单文件压缩失败", e);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("账单文件摘要失败", e);
        }
    }

    private record MatchingResult(int totalRows, int matched, int breaks, int unresolved,
                                  long unmatchedAmount) {
    }
}
