package com.wherelee.edu.modules.platform.aop;

import com.wherelee.edu.modules.platform.audit.AdminAuditRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditAspectTest {

    @Mock
    private AdminAuditRepository repository;
    @Mock
    private ProceedingJoinPoint joinPoint;

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void recordsOperatorTargetAndTraceId() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", "trace-123");

        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, 2L, "body"});
        when(joinPoint.proceed()).thenReturn(null);
        Method method = getClass().getDeclaredMethod("sample", Long.class, Long.class, Object.class);
        AdminAudit audit = method.getAnnotation(AdminAudit.class);
        AdminAuditAspect aspect = new AdminAuditAspect(repository);

        aspect.around(joinPoint, audit);

        verify(repository).insert(
                eq(1L), eq("USER_STATUS_CHANGE"), eq("USER"), eq(2L),
                eq(true), anyLong(), isNull(), eq("trace-123"));
    }

    @Test
    void recordsTargetIdReturnedByCreateOperation() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(42L);
        Method method = getClass().getDeclaredMethod("createSample");
        AdminAudit audit = method.getAnnotation(AdminAudit.class);
        AdminAuditAspect aspect = new AdminAuditAspect(repository);

        aspect.around(joinPoint, audit);

        verify(repository).insert(
                eq(1L), eq("USER_CREATE"), eq("USER"), eq(42L),
                eq(true), anyLong(), isNull(), isNull());
    }

    @AdminAudit(action = "USER_STATUS_CHANGE", targetType = "USER", targetIdArg = 1)
    void sample(Long operatorId, Long targetId, Object body) {
    }

    @AdminAudit(action = "USER_CREATE", targetType = "USER", targetIdFromResult = true)
    Long createSample() {
        return 42L;
    }
}
