package com.ecommerce.orders.infrastructure.client.dto;

import java.math.BigDecimal;

public record ProductResponse(String id, String name, boolean available, PriceDto price) {

    public record PriceDto(BigDecimal amount, String currency) {}
}
