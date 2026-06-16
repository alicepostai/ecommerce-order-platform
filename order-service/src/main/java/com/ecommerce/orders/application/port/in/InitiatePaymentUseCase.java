package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.InitiatePaymentCommand;
import com.ecommerce.orders.application.dto.PaymentResult;

public interface InitiatePaymentUseCase {
    PaymentResult initiatePayment(InitiatePaymentCommand command);
}
