package com.ecommerce.orders.domain.exception;

public class ActiveOrderExistsException extends DomainException {

    public ActiveOrderExistsException(String customerId) {
        super("Customer " + customerId + " already has an active order");
    }
}
