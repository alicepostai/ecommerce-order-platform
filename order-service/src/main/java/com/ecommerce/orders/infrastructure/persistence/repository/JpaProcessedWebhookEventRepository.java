package com.ecommerce.orders.infrastructure.persistence.repository;

import com.ecommerce.orders.infrastructure.persistence.entity.ProcessedWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaProcessedWebhookEventRepository
        extends JpaRepository<ProcessedWebhookEventEntity, String> {

    boolean existsByEventId(String eventId);
}
