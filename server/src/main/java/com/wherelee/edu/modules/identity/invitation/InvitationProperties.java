package com.wherelee.edu.modules.identity.invitation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.teacher-invitation")
public record InvitationProperties(
        String baseUrl,
        int expiryHours,
        int maxAttempts,
        int windowMinutes
) {
    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("缺少教师邀请基础地址 app.teacher-invitation.base-url");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public int safeExpiryHours() {
        return Math.max(1, Math.min(expiryHours, 168));
    }

    public int safeMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public int safeWindowMinutes() {
        return Math.max(1, windowMinutes);
    }
}
