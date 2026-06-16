package com.ecommerce.orders.application.dto;

import java.util.UUID;

public record AddOrderItemCommand(UUID orderId, UUID productId, int quantity) {}
