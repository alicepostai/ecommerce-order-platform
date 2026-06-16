package com.ecommerce.orders.application.dto;

import java.util.UUID;

public record RemoveOrderItemCommand(UUID orderId, UUID itemId) {}
