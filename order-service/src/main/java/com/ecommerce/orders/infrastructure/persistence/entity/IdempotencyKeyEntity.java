package com.ecommerce.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyEntity.IdempotencyKeyId.class)
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "\"key\"", nullable = false, length = 80)
    private String key;

    @Id
    @Column(name = "endpoint_scope", nullable = false, length = 120)
    private String endpointScope;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(String key, String endpointScope, String requestHash,
                                int responseStatus, String responseBody) {
        this.key = key;
        this.endpointScope = endpointScope;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public String getKey() { return key; }
    public String getEndpointScope() { return endpointScope; }
    public String getRequestHash() { return requestHash; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }

    public static class IdempotencyKeyId implements Serializable {
        private String key;
        private String endpointScope;

        protected IdempotencyKeyId() {}

        public IdempotencyKeyId(String key, String endpointScope) {
            this.key = key;
            this.endpointScope = endpointScope;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IdempotencyKeyId that)) return false;
            return Objects.equals(key, that.key) && Objects.equals(endpointScope, that.endpointScope);
        }

        @Override
        public int hashCode() { return Objects.hash(key, endpointScope); }
    }
}
