package com.wherelee.edu.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alert")
public record AlertProperties(
        boolean enabled,
        String webhookUrl,
        int windowMinutes,
        int errorThreshold,
        int stallThreshold,
        double cacheMissRateThreshold,
        int minCacheRequests,
        int dedupMinutes,
        long evaluateIntervalMs,
        long initialDelayMs
) {
}