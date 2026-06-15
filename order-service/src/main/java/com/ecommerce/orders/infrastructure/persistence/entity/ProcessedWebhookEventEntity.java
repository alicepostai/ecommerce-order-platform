package com.ecommerce.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 80)
    private String eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedWebhookEventEntity() {}

    public ProcessedWebhookEventEntity(String eventId, UUID paymentId) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public UUID getPaymentId() { return paymentId; }
    public Instant getProcessedAt() { return processedAt; }
}
