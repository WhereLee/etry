package com.wherelee.edu.audit;

import com.wherelee.edu.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 审计切面。Order 比权限切面靠后：没有权限的操作直接 403，不产生审计记录。
 */
@Slf4j
@Aspect
@Component
@Order(20)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditLog)")
    public Object record(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 审计失败绝不能影响业务：吞掉异常只打日志
            try {
                auditService.record(build(joinPoint, auditLog, success, cost));
            } catch (Exception e) {
                log.error("审计日志写入失败 action={}", auditLog.action(), e);
            }
        }
    }

    private AuditRecord build(ProceedingJoinPoint joinPoint, AuditLog auditLog, boolean success, long costMs) {
        AuditRecord record = new AuditRecord();
        UserContext.Principal principal = UserContext.get();
        if (principal != null) {
            record.setUserId(principal.userId());
            record.setUsername(principal.username());
        }
        record.setAction(auditLog.action());
        record.setTargetType(auditLog.targetType());
        record.setTargetId(extractTargetId(joinPoint, auditLog.targetIdIndex()));
        record.setDetail(summarize(joinPoint.getArgs()));
        record.setIp(currentIp());
        record.setSuccess(success);
        record.setCostMs((int) costMs);
        return record;
    }

    private Long extractTargetId(ProceedingJoinPoint joinPoint, int index) {
        if (index < 0) {
            return null;
        }
        Object[] args = joinPoint.getArgs();
        if (index >= args.length || args[index] == null) {
            return null;
        }
        Object value = args[index];
        if (value instanceof Long l) {
            return l;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 只记录参数摘要，不落敏感内容（比如密码原文） */
    private String summarize(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String text = Arrays.stream(args)
                .map(a -> a == null ? "null" : a.getClass().getSimpleName())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        return text;
    }

    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
