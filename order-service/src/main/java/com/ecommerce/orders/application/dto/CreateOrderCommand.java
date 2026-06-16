package com.ecommerce.orders.application.dto;

import java.util.UUID;

public record CreateOrderCommand(UUID customerId) {}
