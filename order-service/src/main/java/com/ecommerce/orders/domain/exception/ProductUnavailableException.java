package com.ecommerce.orders.domain.exception;

public class ProductUnavailableException extends DomainException {

    public ProductUnavailableException(String productId) {
        super("Product unavailable: " + productId);
    }
}
