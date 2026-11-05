package com.wherelee.edu.modules.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        List<String> corsAllowedOrigins,
        boolean trustForwardedHeaders,
        boolean loginProtectionEnabled,
        int loginMaxAttempts,
        int loginIpMaxAttempts,
        int loginWindowMinutes
) {
    public List<String> allowedOrigins() {
        return corsAllowedOrigins == null ? List.of() : corsAllowedOrigins;
    }
}
