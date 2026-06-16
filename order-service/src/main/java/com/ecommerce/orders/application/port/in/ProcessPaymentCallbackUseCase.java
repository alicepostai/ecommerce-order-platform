package com.ecommerce.orders.application.port.in;

import com.ecommerce.orders.application.dto.ProcessPaymentCallbackCommand;

public interface ProcessPaymentCallbackUseCase {
    void process(ProcessPaymentCallbackCommand command);
}
