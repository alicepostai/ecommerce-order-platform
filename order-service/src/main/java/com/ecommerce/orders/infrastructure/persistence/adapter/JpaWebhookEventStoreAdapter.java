package com.ecommerce.orders.infrastructure.persistence.adapter;

import com.ecommerce.orders.application.port.out.WebhookEventStore;
import com.ecommerce.orders.infrastructure.persistence.entity.ProcessedWebhookEventEntity;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaProcessedWebhookEventRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaWebhookEventStoreAdapter implements WebhookEventStore {

    private final JpaProcessedWebhookEventRepository repo;

    public JpaWebhookEventStoreAdapter(JpaProcessedWebhookEventRepository repo) {
        this.repo = repo;
    }

    @Override
    public boolean isProcessed(String eventId) {
        return repo.existsByEventId(eventId);
    }

    @Override
    public void markProcessed(String eventId, UUID paymentId) {
        repo.save(new ProcessedWebhookEventEntity(eventId, paymentId));
    }
}
