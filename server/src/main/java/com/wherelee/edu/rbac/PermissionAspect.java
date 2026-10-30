package com.wherelee.edu.rbac;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(10)
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionService permissionService;

    @Before("@annotation(requirePermission)")
    public void check(JoinPoint joinPoint, RequirePermission requirePermission) {
        String required = requirePermission.value();
        if (!permissionService.has(required)) {
            log.warn("权限不足 required={} method={}", required, joinPoint.getSignature().toShortString());
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
    }
}
