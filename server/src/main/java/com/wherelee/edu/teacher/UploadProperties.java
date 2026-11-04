package com.wherelee.edu.teacher;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        long maxSizeBytes,
        int urlTtlMinutes,
        List<String> allowedContentTypes
) {
    public long safeMaxSizeBytes() {
        return maxSizeBytes > 0 ? maxSizeBytes : 4L * 1024 * 1024 * 1024;
    }

    public int safeUrlTtlMinutes() {
        return Math.max(1, Math.min(urlTtlMinutes, 120));
    }

    public List<String> allowedTypes() {
        return allowedContentTypes == null || allowedContentTypes.isEmpty()
                ? List.of("video/mp4", "video/webm")
                : allowedContentTypes;
    }
}
