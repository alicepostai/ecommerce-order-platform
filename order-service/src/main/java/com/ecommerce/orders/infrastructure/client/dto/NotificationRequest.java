package com.ecommerce.orders.infrastructure.client.dto;

import java.util.Map;

public record NotificationRequest(
        String recipientId,
        String channel,
        String template,
        Map<String, String> payload) {}
