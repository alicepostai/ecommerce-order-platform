package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.CancellationReason;
import com.ecommerce.orders.domain.model.OrderId;

import java.time.Instant;

public record OrderCancelled(OrderId orderId, CancellationReason reason, Instant occurredAt) implements DomainEvent {

    public OrderCancelled(OrderId orderId, CancellationReason reason) {
        this(orderId, reason, Instant.now());
    }
}
