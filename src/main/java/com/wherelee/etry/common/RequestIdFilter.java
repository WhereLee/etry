package com.wherelee.etry.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求一个可追踪的 id，并把方法、路径、耗时、状态码打进一条日志，
 * 同时回写响应头 X-Request-Id，方便按 id 串前后端日志。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        long startedAt = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - startedAt;
            log.info("{} {} -> {} {}ms requestId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), cost, requestId);
            MDC.remove(MDC_KEY);
        }
    }
}
