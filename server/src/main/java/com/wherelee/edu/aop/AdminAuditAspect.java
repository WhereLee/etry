package com.wherelee.edu.aop;

import com.wherelee.edu.admin.AdminAuditRepository;
import com.wherelee.edu.common.web.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@Order(20)
public class AdminAuditAspect {

    private final AdminAuditRepository repository;

    public AdminAuditAspect(AdminAuditRepository repository) {
        this.repository = repository;
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, AdminAudit audit) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        Long operatorId = attributes == null
                ? null
                : (Long) attributes.getRequest().getAttribute("userId");
        Long argumentTargetId = targetId(joinPoint.getArgs(), audit.targetIdArg());
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            Long targetId = audit.targetIdFromResult() ? resultId(result) : argumentTargetId;
            record(operatorId, targetId, audit, true, start, null);
            return result;
        } catch (Throwable e) {
            record(operatorId, argumentTargetId, audit, false, start, e.getMessage());
            throw e;
        }
    }

    private void record(Long operatorId, Long targetId, AdminAudit audit,
                        boolean success, long start, String error) {
        if (operatorId == null) {
            return;
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        try {
            repository.insert(operatorId, audit.action(), audit.targetType(), targetId,
                    success, durationMs, limit(error, 500), TraceIdFilter.currentTraceId());
        } catch (Exception e) {
            log.warn("管理员审计写入失败 action={} operator={}", audit.action(), operatorId, e);
        }
    }

    private Long targetId(Object[] args, int index) {
        if (index < 0 || index >= args.length || !(args[index] instanceof Number value)) {
            return null;
        }
        return value.longValue();
    }

    private Long resultId(Object result) {
        if (result instanceof AuditedResult auditedResult) {
            return auditedResult.auditTargetId();
        }
        return result instanceof Number value ? value.longValue() : null;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
