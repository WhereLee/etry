package com.wherelee.edu.audit.dto;

import com.wherelee.edu.audit.AuditRecord;

import java.time.LocalDateTime;

public record AuditLogVO(
        Long id,
        Long userId,
        String username,
        String action,
        String targetType,
        Long targetId,
        String detail,
        String ip,
        Boolean success,
        Integer costMs,
        LocalDateTime createdAt
) {

    public static AuditLogVO from(AuditRecord r) {
        return new AuditLogVO(r.getId(), r.getUserId(), r.getUsername(), r.getAction(),
                r.getTargetType(), r.getTargetId(), r.getDetail(), r.getIp(),
                r.getSuccess(), r.getCostMs(), r.getCreatedAt());
    }
}
