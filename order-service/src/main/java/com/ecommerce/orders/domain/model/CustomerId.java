package com.ecommerce.orders.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId must not be null");
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(String uuid) {
        return new CustomerId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
