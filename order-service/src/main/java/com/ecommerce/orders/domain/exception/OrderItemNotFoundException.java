package com.ecommerce.orders.domain.exception;

import com.ecommerce.orders.domain.model.OrderItemId;

public class OrderItemNotFoundException extends DomainException {

    public OrderItemNotFoundException(OrderItemId itemId) {
        super("Order item not found: " + itemId);
    }
}
