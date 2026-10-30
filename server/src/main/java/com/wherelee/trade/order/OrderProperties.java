package com.wherelee.trade.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(int expireMinutes) {
}
