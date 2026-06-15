package com.ecommerce.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_currency", length = 3)
    private String totalCurrency;

    @Column(name = "payment_attempts", nullable = false)
    private int paymentAttempts;

    @Column(name = "cancellation_reason", length = 40)
    private String cancellationReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}

    public OrderEntity(UUID id, UUID customerId, String status, BigDecimal totalAmount,
                       String totalCurrency, int paymentAttempts, String cancellationReason) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.totalCurrency = totalCurrency;
        this.paymentAttempts = paymentAttempts;
        this.cancellationReason = cancellationReason;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getTotalCurrency() { return totalCurrency; }
    public int getPaymentAttempts() { return paymentAttempts; }
    public String getCancellationReason() { return cancellationReason; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItemEntity> getItems() { return items; }

    public void setStatus(String status) { this.status = status; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setTotalCurrency(String totalCurrency) { this.totalCurrency = totalCurrency; }
    public void setPaymentAttempts(int paymentAttempts) { this.paymentAttempts = paymentAttempts; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
}
