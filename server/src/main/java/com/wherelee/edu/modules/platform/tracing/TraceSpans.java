package com.wherelee.edu.modules.platform.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 轻量业务 span 封装。只在支付、退款、补偿和播放地址等关键边界使用，
 * 不把每个方法都包一层，避免 trace 噪声和额外开销。
 */
@Component
public class TraceSpans {

    private final Tracer tracer;

    public TraceSpans(Tracer tracer) {
        this.tracer = tracer;
    }

    public static TraceSpans noop() {
        return new TraceSpans(Tracer.NOOP);
    }

    public static Map<String, String> tags(String... pairs) {
        Map<String, String> tags = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                tags.put(pairs[i], pairs[i + 1]);
            }
        }
        return tags;
    }

    public <T> T trace(String name, Map<String, String> tags, Supplier<T> action) {
        return trace(name, tags, span -> action.get());
    }

    public <T> T trace(String name, Map<String, String> tags, Function<Span, T> action) {
        Span span = tracer.nextSpan().name(name).start();
        String previousTraceId = MDC.get("traceId");
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            applyTags(span, tags);
            String spanTraceId = span.context() == null ? null : span.context().traceId();
            if (spanTraceId != null && !spanTraceId.isBlank()) {
                MDC.put("traceId", spanTraceId);
                span.tag("btry.trace_id", spanTraceId);
            }
            return action.apply(span);
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            if (previousTraceId == null) {
                MDC.remove("traceId");
            } else {
                MDC.put("traceId", previousTraceId);
            }
            span.end();
        }
    }

    public void trace(String name, Map<String, String> tags, Runnable action) {
        trace(name, tags, span -> {
            action.run();
            return null;
        });
    }

    private static void applyTags(Span span, Map<String, String> tags) {
        if (tags == null) {
            return;
        }
        tags.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                span.tag(key, value);
            }
        });
    }
}
