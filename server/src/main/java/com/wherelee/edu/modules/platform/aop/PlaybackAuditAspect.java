package com.wherelee.edu.modules.platform.aop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PlaybackAuditAspect {

    private final MeterRegistry registry;
    private final Timer timer;
    private final Counter failures;

    public PlaybackAuditAspect(MeterRegistry registry) {
        this.registry = registry;
        this.timer = Timer.builder("playback.audit").register(registry);
        this.failures = Counter.builder("playback.audit.failures").register(registry);
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, PlaybackAudit audit) throws Throwable {
        Timer.Sample sample = Timer.start(registry);
        try {
            Object result = joinPoint.proceed();
            long elapsed = sample.stop(timer);
            long ms = elapsed / 1_000_000L;
            if (ms >= 500) {
                log.warn("播放审计慢请求 action={} method={} durationMs={}",
                        audit.value(), joinPoint.getSignature().toShortString(), ms);
            } else {
                log.debug("播放审计 action={} method={} durationMs={}",
                        audit.value(), joinPoint.getSignature().toShortString(), ms);
            }
            return result;
        } catch (Throwable e) {
            sample.stop(timer);
            failures.increment();
            log.warn("播放审计失败 action={} method={} error={}",
                    audit.value(), joinPoint.getSignature().toShortString(), e.getMessage());
            throw e;
        }
    }
}