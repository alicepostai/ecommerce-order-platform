package com.ecommerce.orders.domain.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
