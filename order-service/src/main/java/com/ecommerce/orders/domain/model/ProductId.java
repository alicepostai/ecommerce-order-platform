package com.ecommerce.orders.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) {

    public ProductId {
        Objects.requireNonNull(value, "ProductId must not be null");
    }

    public static ProductId generate() {
        return new ProductId(UUID.randomUUID());
    }

    public static ProductId of(String uuid) {
        return new ProductId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
