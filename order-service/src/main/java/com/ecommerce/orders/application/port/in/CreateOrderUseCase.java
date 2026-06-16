package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.CreateOrderCommand;
import com.ecommerce.orders.application.dto.OrderResult;

public interface CreateOrderUseCase {
    OrderResult create(CreateOrderCommand command);
}
