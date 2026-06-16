package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.AddOrderItemCommand;
import com.ecommerce.orders.application.dto.OrderResult;

public interface AddOrderItemUseCase {
    OrderResult addItem(AddOrderItemCommand command);
}
