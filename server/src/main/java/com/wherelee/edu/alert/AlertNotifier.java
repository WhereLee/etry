package com.wherelee.edu.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class AlertNotifier {

    private final AlertProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AlertNotifier(AlertProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public DeliveryResult send(String message) {
        String webhook = properties.webhookUrl();
        if (webhook == null || webhook.isBlank()) {
            return new DeliveryResult(false, "webhook not configured");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("text", message));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new DeliveryResult(true, null);
            }
            return new DeliveryResult(false, "HTTP " + response.statusCode());
        } catch (Exception e) {
            log.warn("告警 webhook 发送失败", e);
            return new DeliveryResult(false, e.toString());
        }
    }

    public record DeliveryResult(boolean delivered, String error) {
    }
}