package com.wherelee.edu.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求生成 traceId 压入 MDC，让同一次请求打出的所有日志能串起来。
 * 优先级最高：必须比鉴权过滤器还早，否则鉴权阶段的日志就没 traceId 了。
 *
 * userId 由 JwtAuthFilter 在认证成功后写入同一个 MDC，这里只负责清理。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
        }
    }
}
