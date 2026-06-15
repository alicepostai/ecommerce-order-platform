package com.ecommerce.orders.infrastructure.client.dto;

import java.math.BigDecimal;

public record PaymentGatewayRequest(
        String paymentId,
        String orderId,
        AmountDto amount,
        MethodDto method) {

    public record AmountDto(BigDecimal amount, String currency) {}

    public record MethodDto(String type, String cardToken) {}
}
