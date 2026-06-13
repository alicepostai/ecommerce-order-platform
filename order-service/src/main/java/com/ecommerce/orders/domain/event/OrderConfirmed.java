package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.Money;
import com.ecommerce.orders.domain.model.OrderId;

import java.time.Instant;

public record OrderConfirmed(OrderId orderId, Money total, Instant occurredAt) implements DomainEvent {

    public OrderConfirmed(OrderId orderId, Money total) {
        this(orderId, total, Instant.now());
    }
}
