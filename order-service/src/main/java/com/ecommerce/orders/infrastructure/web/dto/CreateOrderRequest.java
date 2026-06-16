package com.ecommerce.orders.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(@NotNull UUID customerId) {}
