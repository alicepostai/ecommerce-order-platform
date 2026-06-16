package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.PaymentResult;

import java.util.UUID;

public interface GetPaymentUseCase {
    PaymentResult getById(UUID paymentId);
}
