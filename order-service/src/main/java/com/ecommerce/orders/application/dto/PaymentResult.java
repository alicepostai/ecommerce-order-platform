package com.ecommerce.orders.application.dto;

import com.ecommerce.orders.domain.model.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResult(
        UUID id,
        UUID orderId,
        String status,
        BigDecimal amount,
        String currency,
        int attemptNumber,
        String transactionId) {

    public static PaymentResult from(Payment payment) {
        return new PaymentResult(
                payment.getId().value(),
                payment.getOrderId().value(),
                payment.getStatus().name(),
                payment.getAmount().amount(),
                payment.getAmount().currency(),
                payment.getAttemptNumber(),
                payment.getTransactionId());
    }
}
