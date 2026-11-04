package com.wherelee.edu.payment;

import com.wherelee.edu.alert.AlertLogRepository;
import com.wherelee.edu.common.BizException;
import com.wherelee.edu.entitlement.EntitlementRepository;
import com.wherelee.edu.order.OrderRecord;
import com.wherelee.edu.order.OrderRepository;
import com.wherelee.edu.order.OrderService;
import com.wherelee.edu.order.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

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
    private final AlertLogRepository alertLogRepository;

    public RefundService(OrderService orderService,
                         OrderRepository orderRepository,
                         PaymentRepository paymentRepository,
                         EntitlementRepository entitlementRepository,
                         PaymentGateway paymentGateway,
                         PaymentProperties properties,
                         PlatformTransactionManager transactionManager,
                         PaymentMetrics metrics,
                         AlertLogRepository alertLogRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.paymentGateway = paymentGateway;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        this.alertLogRepository = alertLogRepository;
    }

    public RefundRecord refund(String orderNo, Long operatorId, String reason, boolean simulateFailure) {
        OrderRecord order = orderService.require(orderNo);
        if (order.status() != OrderStatus.PAID
                && order.status() != OrderStatus.FULFILLED
                && order.status() != OrderStatus.REFUND_FAILED) {
            throw BizException.badRequest("当前订单状态不能退款");
        }
        RefundRecord refund = paymentRepository.findRefundByOrder(order.id()).orElse(null);
        if (refund != null && "SUCCEEDED".equals(refund.status())) {
            return refund;
        }
        if (refund == null) {
            String refundNo = "REF" + LocalDateTime.now().format(TIME)
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            transactionTemplate.executeWithoutResult(status -> {
                paymentRepository.createRefund(refundNo, order.id(), order.priceCents(), reason, operatorId);
                orderRepository.markRefunding(order.orderNo());
            });
            refund = paymentRepository.findRefundByOrder(order.id())
                    .orElseThrow(() -> new IllegalStateException("退款创建后不可读取"));
        } else {
            Long refundId = refund.id();
            transactionTemplate.executeWithoutResult(status -> {
                paymentRepository.retryRefund(refundId);
                orderRepository.markRefunding(order.orderNo());
            });
            refund = paymentRepository.findRefundByOrder(order.id())
                    .orElseThrow(() -> new IllegalStateException("退款重试后不可读取"));
        }
        return processRefund(order, refund, simulateFailure);
    }

    public void reconcile() {
        for (RefundRecord refund : paymentRepository.findPendingRefunds(
                properties.queryAfterSeconds(), properties.reconcileBatchSize())) {
            OrderRecord order = orderRepository.findById(refund.orderId()).orElse(null);
            if (order != null) {
                processRefund(order, refund, false);
            }
        }
    }

    private RefundRecord processRefund(OrderRecord order, RefundRecord refund, boolean simulateFailure) {
        PaymentGateway.RefundResult result = paymentGateway.refund(order, refund, simulateFailure);
        transactionTemplate.executeWithoutResult(status -> {
            if (result.success()) {
                metrics.refundSucceeded();
                paymentRepository.markRefundSucceeded(refund.id(), result.providerRefundNo());
                orderRepository.markRefunded(order.orderNo());
                entitlementRepository.revokeOrder(
                        order.userId(), order.seriesId(), order.id(),
                        "订单退款：" + refund.refundNo()
                );
            } else {
                metrics.refundFailed();
                paymentRepository.markRefundFailed(refund.id(), result.message());
                orderRepository.markRefundFailed(order.orderNo(), result.message());
                alertRefund(order.orderNo(), result.message());
            }
        });
        return paymentRepository.findRefundByOrder(order.id())
                .orElseThrow(() -> new IllegalStateException("退款处理后不可读取"));
    }

    private void alertRefund(String orderNo, String message) {
        try {
            alertLogRepository.insert("PAYMENT_REFUND_FAILED", "ERROR",
                    "订单退款失败 orderNo=" + orderNo + " reason=" + message, false,
                    "webhook delivery not configured for payment alerts");
        } catch (Exception ignored) {
        }
    }
}
