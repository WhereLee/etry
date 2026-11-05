package com.wherelee.edu.modules.commerce.payment;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class PaymentSafetyGuard {

    private static final Set<String> SUPPORTED = Set.of("mock", "alipay");

    private final PaymentProperties properties;
    private final Environment environment;

    public PaymentSafetyGuard(PaymentProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void verify() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        String provider = properties.provider() == null
                ? ""
                : properties.provider().trim().toLowerCase(Locale.ROOT);
        if (prod && ("mock".equals(provider) || properties.mockEnabled())) {
            throw new IllegalStateException("生产环境禁止启用 Mock 支付");
        }
        if (!SUPPORTED.contains(provider)) {
            throw new IllegalStateException("不支持的支付渠道：" + properties.provider());
        }
    }
}
