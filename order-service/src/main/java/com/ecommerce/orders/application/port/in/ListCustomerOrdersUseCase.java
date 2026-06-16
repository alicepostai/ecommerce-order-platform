package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.OrderResult;

import java.util.List;
import java.util.UUID;

public interface ListCustomerOrdersUseCase {
    List<OrderResult> listByCustomer(UUID customerId);
}
