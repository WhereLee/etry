package com.wherelee.edu.modules.platform.aop;

import com.wherelee.edu.modules.platform.common.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(1)
public class RoleAspect {

    @Around("@annotation(requireRole)")
    public Object around(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String role = attributes == null ? null : String.valueOf(attributes.getRequest().getAttribute("role"));
        if (!requireRole.value().equals(role)) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权限执行此操作");
        }
        return joinPoint.proceed();
    }
}