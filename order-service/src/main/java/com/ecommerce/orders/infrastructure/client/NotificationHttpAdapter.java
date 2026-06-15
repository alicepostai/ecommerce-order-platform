package com.ecommerce.orders.infrastructure.client;

import com.ecommerce.orders.application.port.out.NotificationPort;
import com.ecommerce.orders.domain.model.CustomerId;
import com.ecommerce.orders.infrastructure.client.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class NotificationHttpAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationHttpAdapter.class);

    private final RestClient restClient;

    public NotificationHttpAdapter(@Qualifier("notificationRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void send(CustomerId recipientId, NotificationTemplate template, Map<String, String> payload) {
        try {
            var request = new NotificationRequest(
                    recipientId.value().toString(),
                    "EMAIL",
                    template.name(),
                    payload);

            restClient.post()
                    .uri("/notifications")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send notification template={} to recipientId={}: {}",
                    template, recipientId.value(), e.getMessage());
        }
    }
}
