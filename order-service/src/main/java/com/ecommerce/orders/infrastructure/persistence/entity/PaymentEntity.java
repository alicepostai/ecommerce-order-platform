package com.ecommerce.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "card_token", nullable = false)
    private String cardToken;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    public PaymentEntity(UUID id, UUID orderId, String cardToken, String status, int attemptNumber) {
        this.id = id;
        this.orderId = orderId;
        this.cardToken = cardToken;
        this.status = status;
        this.attemptNumber = attemptNumber;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCardToken() { return cardToken; }
    public String getStatus() { return status; }
    public int getAttemptNumber() { return attemptNumber; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
}
