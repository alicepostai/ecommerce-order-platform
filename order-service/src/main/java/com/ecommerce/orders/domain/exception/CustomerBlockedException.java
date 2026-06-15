package com.ecommerce.orders.domain.exception;

public class CustomerBlockedException extends DomainException {

    public CustomerBlockedException(String customerId) {
        super("Customer " + customerId + " is blocked");
    }
}
