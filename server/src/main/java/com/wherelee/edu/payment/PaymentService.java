package com.wherelee.edu.payment;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.entitlement.EntitlementRepository;
import com.wherelee.edu.order.OrderRecord;
import com.wherelee.edu.order.OrderRepository;
import com.wherelee.edu.order.OrderService;
import com.wherelee.edu.order.OrderStatus;
import com.wherelee.edu.order.dto.OrderDtos;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PaymentService {

    private static final List<String> MOCK_SCENARIOS = List.of(
            "SUCCESS",
            "FAILED",
            "LOST_CALLBACK",
            "DELAYED",
            "DUPLICATE",
            "AMOUNT_MISMATCH",
            "FULFILLMENT_FAIL_ONCE"
    );

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntitlementRepository entitlementRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentConfirmationService confirmationService;
    private final OrderFulfillmentService fulfillmentService;
    private final MockSignatureService signatureService;
    private final PaymentProperties properties;
    private final PaymentMetrics metrics;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mock-payment-delay");
        thread.setDaemon(true);
        return thread;
    });

    public PaymentService(OrderService orderService,
                          OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          EntitlementRepository entitlementRepository,
                          PaymentGateway paymentGateway,
                          PaymentConfirmationService confirmationService,
                          OrderFulfillmentService fulfillmentService,
                          MockSignatureService signatureService,
                          PaymentProperties properties,
                          PaymentMetrics metrics) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.paymentGateway = paymentGateway;
        this.confirmationService = confirmationService;
        this.fulfillmentService = fulfillmentService;
        this.signatureService = signatureService;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional
    public OrderDtos.PayResponse pay(Long userId, String orderNo) {
        OrderRecord order = orderService.requireOwned(userId, orderNo);
        if (entitlementRepository.hasActive(userId, order.seriesId())) {
            throw new BizException(HttpStatus.CONFLICT, "你已经拥有这个课程");
        }
        if (order.status() == OrderStatus.PAYING) {
            PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id())
                    .orElseThrow(() -> new BizException(HttpStatus.CONFLICT, "支付尝试不存在，请重新下单"));
            PaymentGateway.PaymentAction action = paymentGateway.initiate(order, attempt);
            return new OrderDtos.PayResponse(order.orderNo(), order.status().name(),
                    paymentGateway.channel(), action.cashierUrl());
        }
        if (order.status() == OrderStatus.FULFILLED) {
            throw new BizException(HttpStatus.CONFLICT, "课程权益已经发放");
        }
        if (order.expired()) {
            orderRepository.close(order.orderNo(), false);
            throw BizException.badRequest("订单已过期，请重新下单");
        }
        int changed = orderRepository.markPaying(order.orderNo(), paymentGateway.channel());
        if (changed == 0) {
            OrderRecord current = orderService.require(orderNo);
            if (current.status() == OrderStatus.PAYING) {
                PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(current.id())
                        .orElseThrow(() -> new BizException(HttpStatus.CONFLICT, "支付尝试不存在，请重新下单"));
                PaymentGateway.PaymentAction action = paymentGateway.initiate(current, attempt);
                return new OrderDtos.PayResponse(current.orderNo(), current.status().name(),
                        paymentGateway.channel(), action.cashierUrl());
            }
            throw new BizException(HttpStatus.CONFLICT, "当前订单状态不能发起支付");
        }

        int attemptNo = paymentRepository.nextAttemptNo(order.id());
        String tradeNo = paymentGateway.outTradeNo(order, attemptNo);
        paymentRepository.createAttempt(
                order.id(), attemptNo, paymentGateway.channel(), tradeNo,
                order.priceCents(), digest(order.orderNo() + "|" + attemptNo + "|" + order.priceCents()));
        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id())
                .orElseThrow(() -> new IllegalStateException("支付尝试创建后不可读取"));
        PaymentGateway.PaymentAction action = paymentGateway.initiate(
                orderRepository.findByOrderNo(orderNo).orElse(order), attempt);
        return new OrderDtos.PayResponse(order.orderNo(), OrderStatus.PAYING.name(),
                paymentGateway.channel(), action.cashierUrl());
    }

    public PaymentDtos.MockCashierView cashier(Long userId, String orderNo) {
        requireMockEnabled();
        OrderRecord order = orderService.requireOwned(userId, orderNo);
        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id())
                .orElseThrow(() -> BizException.badRequest("请先发起支付"));
        if (order.status() != OrderStatus.PAYING || !"PROCESSING".equals(attempt.status())) {
            throw BizException.badRequest("当前订单不在可模拟支付状态");
        }
        return new PaymentDtos.MockCashierView(
                order.orderNo(), order.seriesTitle(), order.priceCents(),
                order.currency(), order.status().name(), MOCK_SCENARIOS
        );
    }

    public PaymentDtos.MockEventResponse simulate(Long userId, String orderNo, String scenario) {
        requireMockEnabled();
        String normalized = scenario == null ? "" : scenario.trim().toUpperCase(Locale.ROOT);
        if (!MOCK_SCENARIOS.contains(normalized)) {
            throw BizException.badRequest("不支持的 Mock 支付场景");
        }
        OrderRecord order = orderService.requireOwned(userId, orderNo);
        if (order.status() != OrderStatus.PAYING) {
            throw BizException.badRequest("订单当前不是支付中状态");
        }
        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id())
                .orElseThrow(() -> BizException.badRequest("支付尝试不存在"));
        if (!"PROCESSING".equals(attempt.status())) {
            throw BizException.badRequest("支付尝试已经结束");
        }

        switch (normalized) {
            case "SUCCESS" -> {
                markProvider(attempt, "PAID", order.priceCents(), normalized);
                finishNotification(successNotification(order, attempt), "模拟支付成功");
            }
            case "FAILED" -> {
                markProvider(attempt, "FAILED", order.priceCents(), normalized);
                finishNotification(failureNotification(order, attempt), "模拟支付失败");
            }
            case "LOST_CALLBACK" -> markProvider(attempt, "PAID", order.priceCents(), normalized);
            case "DELAYED" -> {
                markProvider(attempt, "PAID", order.priceCents(), normalized);
                PaymentNotification notification = successNotification(order, attempt);
                scheduler.schedule(() -> safelyAccept(notification), 5, TimeUnit.SECONDS);
            }
            case "DUPLICATE" -> {
                markProvider(attempt, "PAID", order.priceCents(), normalized);
                PaymentNotification notification = successNotification(order, attempt);
                finishNotification(notification, "第一次支付通知");
                finishNotification(notification, "重复支付通知");
            }
            case "AMOUNT_MISMATCH" -> {
                long wrongAmount = order.priceCents() + 100;
                markProvider(attempt, "PAID", wrongAmount, normalized);
                PaymentNotification notification = new PaymentNotification(
                        paymentGateway.channel(),
                        "MOCK:" + order.orderNo() + ":" + attempt.attemptNo() + ":MISMATCH",
                        order.orderNo(), "PAYMENT_SUCCESS", wrongAmount,
                        attempt.providerTradeNo(), null
                );
                accept(sign(notification));
            }
            case "FULFILLMENT_FAIL_ONCE" -> {
                markProvider(attempt, "PAID", order.priceCents(), normalized);
                accept(successNotification(order, attempt));
            }
            default -> throw BizException.badRequest("不支持的 Mock 支付场景");
        }

        OrderRecord current = orderService.require(orderNo);
        String message = switch (normalized) {
            case "LOST_CALLBACK" -> "平台已扣款但回调丢失，等待主动查单补偿";
            case "DELAYED" -> "支付成功事件将在 5 秒后送达";
            case "DUPLICATE" -> "重复通知已处理，权益不会重复生成";
            case "AMOUNT_MISMATCH" -> "支付金额不一致，订单未发货并已记录异常";
            case "FULFILLMENT_FAIL_ONCE" -> "支付已确认，首次履约失败，等待自动补偿";
            default -> "模拟支付场景已执行";
        };
        return new PaymentDtos.MockEventResponse(orderNo, current.status().name(), message);
    }

    public PaymentConfirmationService.ConfirmationResult accept(PaymentNotification notification) {
        requireMockEnabled();
        if (!signatureService.verify(notification)) {
            metrics.callbackInvalid();
            throw new BizException(HttpStatus.UNAUTHORIZED, "Mock 支付通知签名无效");
        }
        return acceptVerified(notification);
    }

    /**
     * 已完成渠道验签的通知统一走这里：幂等确认 + 履约。
     * Mock 由 MockSignatureService 验签后调用，支付宝由 AlipayNotifyService 验签后调用，
     * 主动查单补偿是服务端内部发起，同样走这里。
     */
    public PaymentConfirmationService.ConfirmationResult acceptVerified(PaymentNotification notification) {
        PaymentConfirmationService.ConfirmationResult result = confirmationService.confirm(notification);
        if (result.success() && result.order() != null
                && (result.order().status() == OrderStatus.PAID
                || result.order().status() == OrderStatus.FULFILLED)) {
            fulfillmentService.fulfill(result.order().orderNo());
        }
        return result;
    }

    public void reconcile() {
        for (OrderRecord order : orderRepository.findPendingQuery(
                properties.queryAfterSeconds(), properties.reconcileBatchSize())) {
            try {
                reconcileOrder(order);
            } catch (Exception e) {
                log.warn("订单主动查单失败 orderNo={}", order.orderNo(), e);
            }
        }
        for (OrderRecord order : orderRepository.findExpired(properties.reconcileBatchSize())) {
            orderRepository.close(order.orderNo(), false);
        }
        for (OrderRecord order : orderRepository.findPaidUnfulfilled(properties.reconcileBatchSize())) {
            try {
                fulfillmentService.fulfill(order.orderNo());
            } catch (Exception e) {
                log.warn("订单补履约失败 orderNo={}", order.orderNo(), e);
            }
        }
    }

    public OrderRecord reconcileOrder(OrderRecord order) {
        if (order.status() != OrderStatus.PAYING) {
            return order;
        }
        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id()).orElse(null);
        PaymentGateway.PaymentQueryResult result = paymentGateway.query(order, attempt);
        if ("PAID".equals(result.status())) {
            PaymentNotification notification = new PaymentNotification(
                    paymentGateway.channel(),
                    paymentGateway.channel() + "_QUERY:" + order.orderNo() + ":" + result.tradeNo(),
                    order.orderNo(),
                    "PAYMENT_SUCCESS",
                    result.amountCents(),
                    result.tradeNo(),
                    null
            );
            acceptVerified(notification);
        } else if ("FAILED".equals(result.status())) {
            PaymentNotification notification = new PaymentNotification(
                    paymentGateway.channel(),
                    paymentGateway.channel() + "_QUERY_FAIL:" + order.orderNo() + ":" + result.tradeNo(),
                    order.orderNo(),
                    "PAYMENT_FAILED",
                    result.amountCents(),
                    result.tradeNo(),
                    null
            );
            acceptVerified(notification);
        }
        return orderService.require(order.orderNo());
    }

    public OrderRecord queryNow(String orderNo) {
        return reconcileOrder(orderService.require(orderNo));
    }

    private void finishNotification(PaymentNotification notification, String message) {
        PaymentConfirmationService.ConfirmationResult result = accept(notification);
        if (!result.success()) {
            throw new BizException(HttpStatus.CONFLICT, result.message());
        }
        log.info("{} orderNo={} status={}", message, notification.orderNo(),
                result.order() == null ? "UNKNOWN" : result.order().status());
    }

    private void safelyAccept(PaymentNotification notification) {
        try {
            finishNotification(notification, "延迟支付通知");
        } catch (Exception e) {
            log.warn("延迟 Mock 支付通知处理失败 orderNo={}", notification.orderNo(), e);
        }
    }

    private void markProvider(PaymentAttemptRecord attempt, String status, long amount, String scenario) {
        paymentRepository.updateProviderState(attempt.id(), status, amount, scenario);
    }

    private PaymentNotification successNotification(OrderRecord order, PaymentAttemptRecord attempt) {
        return sign(new PaymentNotification(
                paymentGateway.channel(),
                "MOCK:" + order.orderNo() + ":" + attempt.attemptNo() + ":PAID",
                order.orderNo(),
                "PAYMENT_SUCCESS",
                order.priceCents(),
                attempt.providerTradeNo(),
                null
        ));
    }

    private PaymentNotification failureNotification(OrderRecord order, PaymentAttemptRecord attempt) {
        return sign(new PaymentNotification(
                paymentGateway.channel(),
                "MOCK:" + order.orderNo() + ":" + attempt.attemptNo() + ":FAILED",
                order.orderNo(),
                "PAYMENT_FAILED",
                order.priceCents(),
                attempt.providerTradeNo(),
                null
        ));
    }

    private PaymentNotification sign(PaymentNotification notification) {
        String signature = signatureService.sign(notification);
        return new PaymentNotification(
                notification.provider(), notification.eventKey(), notification.orderNo(),
                notification.eventType(), notification.amountCents(), notification.tradeNo(), signature
        );
    }

    private void requireMockEnabled() {
        if (!properties.mockEnabled() || !"mock".equalsIgnoreCase(properties.provider())) {
            throw new BizException(HttpStatus.NOT_FOUND, "Mock 支付未启用");
        }
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
