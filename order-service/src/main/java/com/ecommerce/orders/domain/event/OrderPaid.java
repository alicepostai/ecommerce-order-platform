package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;

import java.time.Instant;

public record OrderPaid(OrderId orderId, Instant occurredAt) implements DomainEvent {

    public OrderPaid(OrderId orderId) {
        this(orderId, Instant.now());
    }
}
