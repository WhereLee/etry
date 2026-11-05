package com.wherelee.edu.modules.platform.common.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceIdFilter() {
        this(null);
    }

    @Autowired
    public TraceIdFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    private String resolveTraceId() {
        if (tracerProvider != null) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span span = tracer.currentSpan();
                if (span != null && span.context() != null) {
                    String traceId = span.context().traceId();
                    if (traceId != null && !traceId.isBlank()) {
                        span.tag("btry.trace_id", traceId);
                        return traceId;
                    }
                }
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
