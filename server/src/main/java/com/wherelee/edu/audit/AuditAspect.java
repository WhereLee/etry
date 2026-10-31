package com.wherelee.edu.audit;

import com.wherelee.edu.common.web.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 审计切面。Order 比权限切面靠后：没有权限的操作直接 403，不产生审计记录。
 *
 * 这里只负责流程（计时、成功与否、吞掉审计自身的异常）和注解解析。
 * 三件具体的事分到了三个类里，便于单独测试：
 *   AuditIdentityResolver  —— 谁干的
 *   AuditDetailFormatter   —— 改了什么（含密码脱敏）
 *   AuditClientIpResolver  —— 从哪来
 */
@Slf4j
@Aspect
@Component
@Order(20)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final AuditIdentityResolver identityResolver;
    private final AuditDetailFormatter detailFormatter;
    private final ClientIpResolver ipResolver;

    @Around("@annotation(auditLog)")
    public Object record(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 审计失败绝不能影响业务：吞掉异常只打日志
            try {
                auditService.record(build(joinPoint, auditLog, success, cost, result));
            } catch (Exception e) {
                log.error("审计日志写入失败 action={}", auditLog.action(), e);
            }
        }
    }

    private AuditRecord build(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                              boolean success, long costMs, Object result) {
        AuditRecord record = new AuditRecord();
        identityResolver.fill(record, joinPoint.getArgs(), result);
        record.setAction(auditLog.action());
        record.setTargetType(auditLog.targetType());
        record.setTargetId(extractTargetId(joinPoint, auditLog.targetIdIndex()));
        record.setDetail(detailFormatter.format(joinPoint.getArgs()));
        record.setIp(ipResolver.resolve());
        record.setSuccess(success);
        record.setCostMs((int) costMs);
        return record;
    }

    /** 注解里用参数下标指定「目标 ID 是第几个入参」，-1 表示不取 */
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
}
