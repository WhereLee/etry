package com.wherelee.edu.order;

import java.util.Set;

public enum OrderStatus {
    CREATED,
    PAYING,
    PAID,
    FULFILLED,
    PAY_FAILED,
    CLOSED,
    REFUNDING,
    REFUNDED,
    REFUND_FAILED;

    private static final Set<OrderStatus> ACTIVE = Set.of(CREATED, PAYING, PAY_FAILED, PAID);

    public boolean active() {
        return ACTIVE.contains(this);
    }

    public boolean paid() {
        return this == PAID || this == FULFILLED || this == REFUNDING || this == REFUNDED || this == REFUND_FAILED;
    }
}
