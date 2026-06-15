package com.ecommerce.orders.application.port.out;

import java.util.Optional;

public interface IdempotencyStore {

    record StoredResponse(String requestHash, int status, String body) {}

    Optional<StoredResponse> find(String key, String endpointScope);

    void store(String key, String endpointScope, String requestHash, int status, String body);
}
