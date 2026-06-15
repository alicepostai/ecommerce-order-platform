package com.ecommerce.orders.infrastructure.persistence.adapter;

import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.domain.event.DomainEvent;
import com.ecommerce.orders.infrastructure.persistence.entity.OutboxEventEntity;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaDomainEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDomainEventPublisher.class);

    private final JpaDomainEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(JpaDomainEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishAll(List<DomainEvent> events, String aggregateType, UUID aggregateId) {
        for (var event : events) {
            var payload = serialize(event);
            var entity = new OutboxEventEntity(
                    UUID.randomUUID(),
                    aggregateType,
                    aggregateId,
                    event.getClass().getSimpleName(),
                    payload,
                    event.occurredAt()
            );
            repository.save(entity);
            log.debug("Published domain event {} for aggregate {} id {}",
                    entity.getEventType(), aggregateType, aggregateId);
        }
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize domain event {}", event.getClass().getSimpleName(), e);
            return "{}";
        }
    }
}
