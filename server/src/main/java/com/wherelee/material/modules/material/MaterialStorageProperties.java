package com.wherelee.material.modules.material;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.material-storage")
public record MaterialStorageProperties(String root) {
}