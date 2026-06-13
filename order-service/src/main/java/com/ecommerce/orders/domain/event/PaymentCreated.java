package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.PaymentId;

import java.time.Instant;

public record PaymentCreated(PaymentId paymentId, OrderId orderId, int attemptNumber, Instant occurredAt)
        implements DomainEvent {

    public PaymentCreated(PaymentId paymentId, OrderId orderId, int attemptNumber) {
        this(paymentId, orderId, attemptNumber, Instant.now());
    }
}
