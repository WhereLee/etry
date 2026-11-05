package com.wherelee.edu.modules.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(String videoKey, int videoDurationSeconds) {
}