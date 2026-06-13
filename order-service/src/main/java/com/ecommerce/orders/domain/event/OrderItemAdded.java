package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.ProductId;
import com.ecommerce.orders.domain.model.Quantity;

import java.time.Instant;

public record OrderItemAdded(OrderId orderId, ProductId productId, Quantity quantity, Instant occurredAt)
        implements DomainEvent {

    public OrderItemAdded(OrderId orderId, ProductId productId, Quantity quantity) {
        this(orderId, productId, quantity, Instant.now());
    }
}
