package com.wherelee.material.modules.operations.admin;

public record OutboxAdminDtos() {

    public record ReplayResponse(Long id, String messageType, String orderNo, String status) {
    }
}
