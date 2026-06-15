package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.model.CustomerId;

public interface CustomerGateway {
    void validateActiveCustomer(CustomerId customerId);
}
