package com.ecommerce.orders.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddOrderItemRequest(@NotNull UUID productId, @Min(1) int quantity) {}
