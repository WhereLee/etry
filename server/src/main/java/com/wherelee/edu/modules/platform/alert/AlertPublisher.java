package com.wherelee.edu.modules.platform.alert;

/**
 * 告警发布端口。
 *
 * <p>业务模块只负责表达“发生了什么”，不关心告警最终是写数据库、
 * 走 webhook 还是被其他运维系统消费。</p>
 */
public interface AlertPublisher {

    void publish(String alertType, String severity, String message,
                 boolean delivered, String deliveryError);
}