package com.ecommerce.orders.application.port.out;

import java.util.UUID;

public interface WebhookEventStore {
    boolean isProcessed(String eventId);
    void markProcessed(String eventId, UUID paymentId);
}
