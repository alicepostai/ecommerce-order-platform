package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.PaymentId;

import java.time.Instant;

public record PaymentCancelled(PaymentId paymentId, OrderId orderId, Instant occurredAt) implements DomainEvent {

    public PaymentCancelled(PaymentId paymentId, OrderId orderId) {
        this(paymentId, orderId, Instant.now());
    }
}
