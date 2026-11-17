package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.commerce.entitlement.EntitlementRepository;
import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderService;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.platform.alert.AlertPublisher;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.tracing.TraceSpans;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class RefundService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntitlementRepository entitlementRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final PaymentMetrics metrics;
    private final AlertPublisher alertPublisher;
    private final TraceSpans traceSpans;

    public RefundService(OrderService orderService,
                         OrderRepository orderRepository,
                         PaymentRepository paymentRepository,
                         EntitlementRepository entitlementRepository,
                         PaymentGateway paymentGateway,
                         PaymentProperties properties,
                         PlatformTransactionManager transactionManager,
                         PaymentMetrics metrics,
                         AlertPublisher alertPublisher,
                         TraceSpans traceSpans) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.paymentGateway = paymentGateway;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        this.alertPublisher = alertPublisher;
        this.traceSpans = traceSpans;
    }

    public RefundRecord refund(String orderNo, Long operatorId, String reason, boolean simulateFailure) {
        return refundOrder(orderNo, operatorId, reason, simulateFailure);
    }

    /**
     * 订单主支付退款：退还订单实际成交的那笔支付，并撤销 ORDER 来源权益。
     */
    public RefundRecord refundOrder(String orderNo, Long operatorId, String reason, boolean simulateFailure) {
        OrderRecord order = orderService.require(orderNo);
        if (order.status() != OrderStatus.PAID
                && order.status() != OrderStatus.FULFILLED
                && order.status() != OrderStatus.REFUND_FAILED) {
            throw BizException.badRequest("当前订单状态不能退款");
        }
        PaymentAttemptRecord attempt = resolvePaidAttempt(order);
        if (attempt.providerAmountCents() <= 0
                || attempt.providerAmountCents() != order.priceCents()) {
            throw new BizException(HttpStatus.CONFLICT,
                    "主支付金额与订单不一致，需人工核对 attemptNo=" + attempt.attemptNo());
        }
        return executeRefund(order, attempt, RefundKind.ORDER, operatorId, reason, simulateFailure);
    }

    /**
     * 重复付款退款：只退指定 attempt 对应的第二笔资金，不改变订单状态，也不撤销课程权益。
     */
    public RefundRecord refundDuplicatePayment(String orderNo, int attemptNo,
                                               Long operatorId, String reason, boolean simulateFailure) {
        OrderRecord order = orderService.require(orderNo);
        if (!order.status().paid()) {
            throw BizException.badRequest("订单尚未支付，不能发起重复付款退款");
        }
        PaymentAttemptRecord attempt = paymentRepository.findAttempts(order.id()).stream()
                .filter(item -> item.attemptNo() == attemptNo)
                .findFirst()
                .orElseThrow(() -> BizException.notFound("支付尝试不存在"));
        if (!"SUCCESS".equals(attempt.status()) || attempt.channelTradeNo() == null
                || attempt.channelTradeNo().isBlank()) {
            throw BizException.badRequest("该支付尝试没有可退款的渠道成功交易");
        }
        if (order.providerTradeNo() == null || order.providerTradeNo().isBlank()) {
            throw new BizException(HttpStatus.CONFLICT,
                    "订单缺少主支付渠道交易号，无法判断重复付款，需人工核对");
        }
        if (order.providerTradeNo().equals(attempt.channelTradeNo())) {
            throw BizException.badRequest("这是订单主支付，请使用订单退款");
        }
        if (attempt.providerAmountCents() != order.priceCents()) {
            throw new BizException(HttpStatus.CONFLICT,
                    "重复付款金额与订单不一致，需人工核对 tradeNo=" + attempt.channelTradeNo());
        }
        return executeRefund(order, attempt, RefundKind.DUPLICATE_PAYMENT,
                operatorId, reason, simulateFailure);
    }

    public void reconcile() {
        for (RefundRecord refund : paymentRepository.findRefundsForReconcile(
                properties.queryAfterSeconds(), properties.safeRefundMaxAttempts(),
                properties.reconcileBatchSize())) {
            String leaseToken = UUID.randomUUID().toString();
            int leaseSeconds = refundLeaseSeconds();
            try {
                if (paymentRepository.claimRefund(refund.id(), leaseToken, leaseSeconds) != 1) {
                    continue;
                }
                RefundRecord claimed = paymentRepository.findRefundById(refund.id())
                        .orElse(refund);
                OrderRecord order = orderRepository.findById(refund.orderId()).orElse(null);
                PaymentAttemptRecord attempt = resolveRefundAttempt(order, claimed);
                if (order == null || attempt == null) {
                    alertRefund(order == null ? "UNKNOWN" : order.orderNo(),
                            "退款记录缺少订单或支付尝试 refundId=" + refund.id());
                    releaseInvalidClaim(refund.id(), leaseToken, "缺少订单或支付尝试");
                    continue;
                }
                processRefund(order, attempt, claimed, leaseToken, false);
            } catch (Exception e) {
                log.error("退款补偿处理失败 refundId={} orderId={}", refund.id(), refund.orderId(), e);
            }
        }
    }

    private RefundRecord executeRefund(OrderRecord order, PaymentAttemptRecord attempt,
                                       RefundKind kind, Long operatorId, String reason,
                                       boolean simulateFailure) {
        RefundRecord refund = paymentRepository.findRefundByAttempt(attempt.id()).orElse(null);
        if (refund != null && "SUCCEEDED".equals(refund.status())) {
            return refund;
        }
        if (refund == null) {
            String refundNo = newRefundNo();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    paymentRepository.createRefund(
                            refundNo, order.id(), attempt.id(), kind,
                            attempt.providerAmountCents(), reason, operatorId);
                    if (kind == RefundKind.ORDER) {
                        orderRepository.markRefunding(order.orderNo());
                    }
                });
            } catch (DuplicateKeyException e) {
                return paymentRepository.findRefundByAttempt(attempt.id())
                        .orElseThrow(() -> new IllegalStateException("并发退款创建后不可读取"));
            }
            refund = paymentRepository.findRefundByAttempt(attempt.id())
                    .orElseThrow(() -> new IllegalStateException("退款创建后不可读取"));
        }

        String leaseToken = UUID.randomUUID().toString();
        if (paymentRepository.claimRefundManual(refund.id(), leaseToken, refundLeaseSeconds()) != 1) {
            return paymentRepository.findRefundByAttempt(attempt.id()).orElse(refund);
        }
        refund = paymentRepository.findRefundByAttempt(attempt.id()).orElse(refund);
        return processRefund(order, attempt, refund, leaseToken, simulateFailure);
    }

    private RefundRecord processRefund(OrderRecord order, PaymentAttemptRecord attempt,
                                       RefundRecord refund, String leaseToken,
                                       boolean simulateFailure) {
        return traceSpans.trace("payment.refund", TraceSpans.tags(
                "order.no", order.orderNo(),
                "refund.no", refund.refundNo(),
                "refund.kind", refund.refundKind().name(),
                "refund.simulate_failure", String.valueOf(simulateFailure)
        ), () -> processRefundInternal(order, attempt, refund, leaseToken, simulateFailure));
    }

    private RefundRecord processRefundInternal(OrderRecord order, PaymentAttemptRecord attempt,
                                               RefundRecord refund, String leaseToken,
                                               boolean simulateFailure) {
        PaymentGateway.RefundResult result = paymentGateway.refund(order, attempt, refund, simulateFailure);
        RefundRecord current = transactionTemplate.execute(status -> {
            OrderRecord lockedOrder = orderRepository.findByOrderNoForUpdate(order.orderNo()).orElse(order);
            RefundRecord lockedRefund = paymentRepository.findRefundByIdForUpdate(refund.id()).orElse(refund);

            if ("SUCCEEDED".equals(lockedRefund.status())) {
                return lockedRefund;
            }
            if (result.success()) {
                metrics.refundSucceeded();
                paymentRepository.markRefundSucceeded(lockedRefund.id(), result.providerRefundNo());
                if (lockedRefund.refundKind() == RefundKind.ORDER) {
                    if (lockedOrder.status() != OrderStatus.REFUNDED
                            && lockedOrder.status() != OrderStatus.REFUNDING) {
                        orderRepository.markRefunding(lockedOrder.orderNo());
                    }
                    orderRepository.markRefunded(lockedOrder.orderNo());
                    entitlementRepository.revokeOrder(
                            lockedOrder.userId(), lockedOrder.seriesId(), lockedOrder.id(),
                            "订单退款：" + lockedRefund.refundNo());
                }
            } else {
                int changed = paymentRepository.markRefundFailed(
                        lockedRefund.id(), leaseToken, result.message(), nextRetryAt(lockedRefund));
                if (changed == 1) {
                    metrics.refundFailed();
                    if (lockedRefund.refundKind() == RefundKind.ORDER) {
                        orderRepository.markRefundFailed(lockedOrder.orderNo(), result.message());
                    }
                    alertRefund(lockedOrder.orderNo(), result.message());
                } else {
                    log.warn("退款失败结果未落账，可能已由其他 worker 成功或租约已变化 refundId={} orderNo={}",
                            lockedRefund.id(), lockedOrder.orderNo());
                }
            }
            return paymentRepository.findRefundByIdForUpdate(lockedRefund.id()).orElse(lockedRefund);
        });

        if (current == null) {
            throw new IllegalStateException("退款处理后不可读取");
        }
        log.info("payment_refund_result orderNo={} refundNo={} kind={} status={} attemptCount={} providerRefundNo={} message={}",
                order.orderNo(), current.refundNo(), current.refundKind(), current.status(),
                current.attemptCount(), current.providerRefundNo(), result.message());
        return current;
    }

    private PaymentAttemptRecord resolvePaidAttempt(OrderRecord order) {
        PaymentAttemptRecord attempt = null;
        if (order.providerTradeNo() != null && !order.providerTradeNo().isBlank()) {
            attempt = paymentRepository.findPaidAttemptByChannelTradeNo(
                    order.id(), order.providerTradeNo()).orElse(null);
        }
        if (attempt == null) {
            var successful = paymentRepository.findSuccessfulAttempts(order.id());
            if (successful.size() == 1) {
                attempt = successful.get(0);
            } else if (successful.size() > 1) {
                throw new BizException(HttpStatus.CONFLICT,
                        "订单存在多笔成功支付且主交易号无法精确匹配，请按支付尝试发起退款");
            }
        }
        if (attempt == null) {
            throw new BizException(HttpStatus.CONFLICT, "找不到订单对应的成功支付尝试，需人工核对");
        }
        if (attempt.channelTradeNo() == null || attempt.channelTradeNo().isBlank()) {
            throw new BizException(HttpStatus.CONFLICT, "成功支付尝试缺少渠道交易号，需人工核对");
        }
        return attempt;
    }

    private PaymentAttemptRecord resolveRefundAttempt(OrderRecord order, RefundRecord refund) {
        if (refund.attemptId() != null) {
            PaymentAttemptRecord attempt = paymentRepository.findAttemptById(refund.attemptId()).orElse(null);
            if (attempt != null) {
                return attempt;
            }
        }
        if (order == null || refund.refundKind() != RefundKind.ORDER) {
            return null;
        }
        PaymentAttemptRecord attempt = resolvePaidAttempt(order);
        paymentRepository.assignRefundAttempt(refund.id(), attempt.id());
        return attempt;
    }

    private void releaseInvalidClaim(Long refundId, String leaseToken, String message) {
        paymentRepository.markRefundFailed(refundId, leaseToken, message, null);
    }

    private int refundLeaseSeconds() {
        return Math.max(120, properties.queryAfterSeconds() * 2);
    }

    private String newRefundNo() {
        return "REF" + LocalDateTime.now().format(TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private LocalDateTime nextRetryAt(RefundRecord refund) {
        int nextAttempt = refund.attemptCount() + 1;
        if (nextAttempt >= properties.safeRefundMaxAttempts()) {
            return null;
        }
        long multiplier = 1L << Math.min(nextAttempt - 1, 6);
        long seconds = Math.min(3600, (long) properties.safeRefundRetryBaseSeconds() * multiplier);
        return LocalDateTime.now().plusSeconds(seconds);
    }

    private void alertRefund(String orderNo, String message) {
        try {
            alertPublisher.publish("PAYMENT_REFUND_FAILED", "ERROR",
                    "订单退款需要处理 orderNo=" + orderNo + " reason=" + message, false,
                    "webhook delivery not configured for payment alerts");
        } catch (Exception ignored) {
        }
    }
}
