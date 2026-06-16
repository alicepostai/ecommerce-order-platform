package com.ecommerce.orders.infrastructure.persistence.adapter;

import com.ecommerce.orders.application.port.out.IdempotencyStore;
import com.ecommerce.orders.infrastructure.persistence.entity.IdempotencyKeyEntity;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaIdempotencyKeyRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class JpaIdempotencyStoreAdapter implements IdempotencyStore {

    private final JpaIdempotencyKeyRepository repository;

    public JpaIdempotencyStoreAdapter(JpaIdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StoredResponse> find(String key, String endpointScope) {
        return repository.findByKeyAndEndpointScope(key, endpointScope)
                .map(e -> new StoredResponse(e.getRequestHash(), e.getResponseStatus(), e.getResponseBody()));
    }

    @Override
    public void store(String key, String endpointScope, String requestHash, int status, String body) {
        repository.save(new IdempotencyKeyEntity(key, endpointScope, requestHash, status, body));
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpired() {
        repository.deleteByCreatedAtBefore(Instant.now().minus(24, ChronoUnit.HOURS));
    }
}
