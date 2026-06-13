package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.CustomerId;
import com.ecommerce.orders.domain.model.OrderId;

import java.time.Instant;

public record OrderCreated(OrderId orderId, CustomerId customerId, Instant occurredAt) implements DomainEvent {

    public OrderCreated(OrderId orderId, CustomerId customerId) {
        this(orderId, customerId, Instant.now());
    }
}
