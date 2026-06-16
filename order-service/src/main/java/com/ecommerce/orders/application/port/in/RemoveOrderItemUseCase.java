package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.dto.RemoveOrderItemCommand;

public interface RemoveOrderItemUseCase {
    OrderResult removeItem(RemoveOrderItemCommand command);
}
