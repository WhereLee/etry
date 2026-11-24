package com.wherelee.material.modules.commerce.payment;

import com.wherelee.material.modules.commerce.entitlement.EntitlementRepository;
import com.wherelee.material.modules.commerce.order.OrderRecord;
import com.wherelee.material.modules.commerce.order.OrderRepository;
import com.wherelee.material.modules.platform.alert.AlertPublisher;
import com.wherelee.material.modules.platform.resilience.LocalFallbackCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class EntitlementConsistencyJob {

    private final OrderRepository orderRepository;
    private final EntitlementRepository entitlementRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final AlertPublisher alertPublisher;
    private final LocalFallbackCache dedupCache;
    private final AtomicLong remainingGauge;
    private final int batchSize;

    public EntitlementConsistencyJob(OrderRepository orderRepository,
                                     EntitlementRepository entitlementRepository,
                                     OrderFulfillmentService fulfillmentService,
                                     AlertPublisher alertPublisher,
                                     LocalFallbackCache dedupCache,
                                     MeterRegistry meterRegistry,
                                     @Value("${app.payment.entitlement-consistency-batch-size:100}") int batchSize) {
        this.orderRepository = orderRepository;
        this.entitlementRepository = entitlementRepository;
        this.fulfillmentService = fulfillmentService;
        this.alertPublisher = alertPublisher;
        this.dedupCache = dedupCache;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.remainingGauge = new AtomicLong();
        Gauge.builder("btry_payment_refunded_active_entitlement", remainingGauge, AtomicLong::doubleValue)
                .description("已退款订单仍存在有效权益的数量")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.payment.entitlement-consistency-interval-ms:60000}",
            initialDelayString = "${app.payment.entitlement-consistency-initial-delay-ms:30000}"
    )
    public void reconcile() {
        try {
            for (OrderRecord order : findCandidates()) {
                if (fulfillmentService.revoke(order.orderNo(), "自动补偿：退款完成但权益仍有效")) {
                    log.info("refunded_entitlement_compensated orderNo={} orderId={}",
                            order.orderNo(), order.id());
                } else {
                    alert(order);
                }
            }
            remainingGauge.set(findCandidates().size());
        } catch (Exception e) {
            log.error("退款权益一致性巡检失败", e);
        }
    }

    private List<OrderRecord> findCandidates() {
        List<OrderRecord> refunded = orderRepository.findRefunded(batchSize);
        if (refunded.isEmpty()) {
            return List.of();
        }
        Set<Long> activeOrderIds = entitlementRepository.findActiveOrderSourceIds(
                refunded.stream().map(OrderRecord::id).toList());
        return refunded.stream().filter(order -> activeOrderIds.contains(order.id())).toList();
    }

    private void alert(OrderRecord order) {
        String key = "refunded-entitlement:" + order.id();
        if (!dedupCache.reserve(key, Duration.ofMinutes(10))) {
            return;
        }
        alertPublisher.publish("PAYMENT_REFUNDED_ENTITLEMENT_ACTIVE", "HIGH",
                "已退款订单仍有有效权益 orderNo=" + order.orderNo(), false,
                "entitlement compensation retry scheduled");
    }
}
