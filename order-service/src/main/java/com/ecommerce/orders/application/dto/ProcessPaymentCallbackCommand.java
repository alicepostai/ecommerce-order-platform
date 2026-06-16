package com.ecommerce.orders.application.dto;

import java.util.UUID;

public record ProcessPaymentCallbackCommand(
        UUID paymentId,
        String eventId,
        String status,
        String transactionId) {}
