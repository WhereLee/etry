package com.wherelee.material.modules.operations.admin;

import com.wherelee.material.modules.commerce.outbox.OutboxMessage;
import com.wherelee.material.modules.commerce.outbox.OutboxReplayService;
import com.wherelee.material.modules.platform.aop.AdminAudit;
import com.wherelee.material.modules.platform.aop.Idempotent;
import com.wherelee.material.modules.platform.aop.Measured;
import com.wherelee.material.modules.platform.aop.RateLimit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/outbox")
public class OutboxAdminController {

    private final OutboxReplayService service;

    public OutboxAdminController(OutboxReplayService service) {
        this.service = service;
    }

    @PostMapping("/{id}/replay")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @RateLimit(key = "admin:outboxReplay", limit = 10, windowSeconds = 60)
    @Idempotent(key = "admin:outboxReplay", windowSeconds = 3)
    @Measured("admin.outbox.replay")
    @AdminAudit(action = "OUTBOX_REPLAY", targetType = "OUTBOX", targetIdArg = 0)
    public OutboxAdminDtos.ReplayResponse replay(@PathVariable Long id) {
        OutboxMessage message = service.replay(id);
        return new OutboxAdminDtos.ReplayResponse(message.id(), message.messageType(),
                message.orderNo(), message.status());
    }
}
