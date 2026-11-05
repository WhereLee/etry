package com.wherelee.edu.modules.platform.alert;

import org.springframework.stereotype.Service;

/**
 * 告警落库适配器。后续接入 webhook、邮件或告警平台时替换此实现即可。
 */
@Service
public class AlertLogPublisher implements AlertPublisher {

    private final AlertLogRepository repository;

    public AlertLogPublisher(AlertLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void publish(String alertType, String severity, String message,
                        boolean delivered, String deliveryError) {
        repository.insert(alertType, severity, message, delivered, deliveryError);
    }
}