package com.ecommerce.orders.domain.model;

public record Quantity(int value) {

    public Quantity {
        if (value <= 0)
            throw new IllegalArgumentException("quantity must be greater than zero, got: " + value);
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public Quantity add(Quantity other) {
        return new Quantity(this.value + other.value);
    }
}
