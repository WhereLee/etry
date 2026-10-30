package com.wherelee.edu.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(int loginFailThreshold, int loginLockMinutes, boolean trustForwardedHeaders) {
}
