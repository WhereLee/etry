package com.wherelee.edu.modules.platform.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MeasuredAspect {

    private final MeterRegistry registry;

    public MeasuredAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("@annotation(measured)")
    public Object around(ProceedingJoinPoint joinPoint, Measured measured) throws Throwable {
        String name = measured.value().isBlank()
                ? joinPoint.getSignature().toShortString()
                : measured.value();
        Timer.Sample sample = Timer.start(registry);
        try {
            return joinPoint.proceed();
        } finally {
            sample.stop(Timer.builder("btry.method")
                    .tag("method", name)
                    .register(registry));
        }
    }
}