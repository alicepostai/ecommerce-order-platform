package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.model.CustomerId;

import java.util.Map;

public interface NotificationPort {

    enum NotificationTemplate { ORDER_CONFIRMED, ORDER_PAID, ORDER_CANCELLED }

    void send(CustomerId recipientId, NotificationTemplate template, Map<String, String> payload);
}
