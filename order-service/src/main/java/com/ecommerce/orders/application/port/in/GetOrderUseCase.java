package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.OrderResult;

import java.util.UUID;

public interface GetOrderUseCase {
    OrderResult getById(UUID orderId);
}
