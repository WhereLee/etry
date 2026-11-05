package com.wherelee.edu.modules.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.playback")
public record PlaybackProperties(int urlCacheMarginMinutes, int logRetentionDays) {
}