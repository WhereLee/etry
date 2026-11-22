package com.wherelee.material.modules.commerce.outbox;

import com.wherelee.material.modules.commerce.payment.OrderFulfillmentService;
import com.wherelee.material.modules.platform.alert.AlertPublisher;
import com.wherelee.material.modules.platform.tracing.TraceSpans;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 发件箱投递器（relay）。租约认领一批消息，按类型幂等投递：
 * FULFILL → 订单履约，REVOKE → 权益撤销。投递成功标 DONE；失败按退避重试，
 * 重试耗尽标 DEAD 并告警转人工。崩溃后租约过期，消息可被重新认领，意图不丢。
 */
@Slf4j
@Service
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final OutboxProperties properties;
    private final AlertPublisher alertPublisher;
    private final TraceSpans traceSpans;

    public OutboxRelay(OutboxRepository outboxRepository,
                       OrderFulfillmentService fulfillmentService,
                       OutboxProperties properties,
                       AlertPublisher alertPublisher,
                       TraceSpans traceSpans) {
        this.outboxRepository = outboxRepository;
        this.fulfillmentService = fulfillmentService;
        this.properties = properties;
        this.alertPublisher = alertPublisher;
        this.traceSpans = traceSpans;
    }

    public int drain() {
        return traceSpans.trace("outbox.drain", TraceSpans.tags(), this::drainInternal);
    }

    private int drainInternal() {
        String leaseToken = UUID.randomUUID().toString();
        List<OutboxMessage> batch = outboxRepository.claimBatch(
                leaseToken, properties.safeBatchSize(), properties.safeLeaseSeconds());
        int delivered = 0;
        for (OutboxMessage message : batch) {
            if (deliver(message, leaseToken)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean deliver(OutboxMessage message, String leaseToken) {
        try {
            if (dispatch(message)) {
                outboxRepository.markDone(message.id(), leaseToken);
                return true;
            }
            handleFailure(message, leaseToken, "投递返回未成功");
            return false;
        } catch (Exception e) {
            log.error("发件箱投递异常 outboxId={} type={} orderNo={}",
                    message.id(), message.messageType(), message.orderNo(), e);
            handleFailure(message, leaseToken, truncate(e.getMessage()));
            return false;
        }
    }

    private boolean dispatch(OutboxMessage message) {
        return switch (message.messageType()) {
            case OutboxMessage.TYPE_FULFILL -> fulfillmentService.fulfill(message.orderNo());
            case OutboxMessage.TYPE_REVOKE -> fulfillmentService.revoke(message.orderNo(), message.reason());
            default -> false;
        };
    }

    private void handleFailure(OutboxMessage message, String leaseToken, String error) {
        if (message.retryCount() >= message.maxRetry()) {
            outboxRepository.markDead(message.id(), leaseToken, error);
            log.error("发件箱消息重试耗尽转 DEAD outboxId={} type={} orderNo={}",
                    message.id(), message.messageType(), message.orderNo());
            alert("OUTBOX_DEAD", "发件箱消息重试耗尽需人工处理 outboxId=" + message.id()
                    + " type=" + message.messageType() + " orderNo=" + message.orderNo()
                    + " error=" + error);
        } else {
            outboxRepository.markRetry(message.id(), leaseToken, error, nextRetryAt(message.retryCount()));
        }
    }

    private LocalDateTime nextRetryAt(int retryCount) {
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 6);
        long seconds = Math.min(3600, (long) properties.safeRetryBaseSeconds() * multiplier);
        return LocalDateTime.now().plusSeconds(seconds);
    }

    private String truncate(String value) {
        if (value == null) {
            return "投递失败";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void alert(String type, String message) {
        try {
            alertPublisher.publish(type, "ERROR", message, false,
                    "webhook delivery not configured for outbox alerts");
        } catch (Exception e) {
            log.warn("发件箱告警入库失败 type={} message={}", type, message, e);
        }
    }
}