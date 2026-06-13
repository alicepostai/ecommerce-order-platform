package com.ecommerce.orders.domain.model;

import java.util.Objects;

public record ProductSnapshot(ProductId productId, String productName, Money unitPrice) {

    public ProductSnapshot {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(productName, "productName must not be null");
        if (productName.isBlank())
            throw new IllegalArgumentException("productName must not be blank");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
    }
}
