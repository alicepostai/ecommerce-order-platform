package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;

import java.time.Instant;

public record PaymentInitiated(OrderId orderId, Instant occurredAt) implements DomainEvent {

    public PaymentInitiated(OrderId orderId) {
        this(orderId, Instant.now());
    }
}
