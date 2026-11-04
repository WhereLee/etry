package com.wherelee.edu.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cos")
public record CosProperties(
        String bucket,
        String region,
        int urlTtlMinutes,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxRetries
) {
}