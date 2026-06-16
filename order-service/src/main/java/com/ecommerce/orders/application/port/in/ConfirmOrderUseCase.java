package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.ConfirmOrderCommand;
import com.ecommerce.orders.application.dto.OrderResult;

public interface ConfirmOrderUseCase {
    OrderResult confirm(ConfirmOrderCommand command);
}
