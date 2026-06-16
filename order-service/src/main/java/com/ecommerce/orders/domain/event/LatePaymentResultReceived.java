package com.ecommerce.orders.domain.event;

import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.PaymentId;

import java.time.Instant;

public record LatePaymentResultReceived(PaymentId paymentId, OrderId orderId, String webhookStatus)
        implements DomainEvent {

    @Override
    public Instant occurredAt() {
        return Instant.now();
    }
}
