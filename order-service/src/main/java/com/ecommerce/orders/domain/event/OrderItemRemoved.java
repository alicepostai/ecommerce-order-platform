package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.OrderItemId;

import java.time.Instant;

public record OrderItemRemoved(OrderId orderId, OrderItemId itemId, Instant occurredAt) implements DomainEvent {

    public OrderItemRemoved(OrderId orderId, OrderItemId itemId) {
        this(orderId, itemId, Instant.now());
    }
}
