package com.wherelee.material.modules.commerce.outbox;

import com.wherelee.material.modules.platform.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class OutboxReplayService {

    private final OutboxRepository repository;
    private final OutboxTransport transport;
    private final OutboxProperties properties;

    public OutboxReplayService(OutboxRepository repository, OutboxTransport transport,
                               OutboxProperties properties) {
        this.repository = repository;
        this.transport = transport;
        this.properties = properties;
    }

    public OutboxMessage replay(Long id) {
        OutboxMessage message = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("发件箱消息不存在"));
        String leaseToken = UUID.randomUUID().toString();
        if (repository.claimReplay(id, leaseToken, properties.safeLeaseSeconds()) != 1) {
            throw new BizException(HttpStatus.CONFLICT, "只有已投递或死信消息可以重放，且当前不可重放");
        }
        try {
            if (transport.deliver(message)) {
                if (repository.markDone(id, leaseToken) != 1) {
                    throw new BizException(HttpStatus.CONFLICT, "重放租约已变化，请刷新后重试");
                }
            } else if (repository.markDead(id, leaseToken, "manual replay failed") != 1) {
                throw new BizException(HttpStatus.CONFLICT, "重放租约已变化，请刷新后重试");
            }
        } catch (Exception e) {
            repository.markDead(id, leaseToken, truncate(e.getMessage()));
            log.error("人工重放发件箱失败 outboxId={} orderNo={}", id, message.orderNo(), e);
            throw new BizException(HttpStatus.CONFLICT, "发件箱重放失败，请检查 MQ 与业务日志");
        }
        log.info("outbox_replayed outboxId={} type={} orderNo={}", id,
                message.messageType(), message.orderNo());
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("发件箱重放后不可读取"));
    }

    private String truncate(String value) {
        if (value == null) {
            return "manual replay failed";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
