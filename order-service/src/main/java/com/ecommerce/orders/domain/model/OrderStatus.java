package com.ecommerce.orders.domain.model;

public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PAYMENT_PENDING,
    PAID,
    CANCELLED;

    public boolean isActive() {
        return this == CREATED || this == CONFIRMED || this == PAYMENT_PENDING;
    }

    public boolean isTerminal() {
        return this == PAID || this == CANCELLED;
    }
}
