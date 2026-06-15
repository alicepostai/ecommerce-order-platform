package com.ecommerce.orders.domain.exception;

public class ProductNotFoundException extends DomainException {

    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}
