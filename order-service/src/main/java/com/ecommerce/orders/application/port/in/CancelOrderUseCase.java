package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.CancelOrderCommand;

public interface CancelOrderUseCase {
    void cancel(CancelOrderCommand command);
}
