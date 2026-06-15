package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public interface DomainEventPublisher {
    void publishAll(List<DomainEvent> events, String aggregateType, UUID aggregateId);
}
