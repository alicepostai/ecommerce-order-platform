package com.ecommerce.orders.domain.model;

import java.util.Objects;
import java.util.UUID;

public record OrderItemId(UUID value) {

    public OrderItemId {
        Objects.requireNonNull(value, "OrderItemId must not be null");
    }

    public static OrderItemId generate() {
        return new OrderItemId(UUID.randomUUID());
    }

    public static OrderItemId of(String uuid) {
        return new OrderItemId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
