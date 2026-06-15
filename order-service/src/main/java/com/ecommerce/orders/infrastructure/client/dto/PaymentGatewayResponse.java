package com.ecommerce.orders.infrastructure.client.dto;

public record PaymentGatewayResponse(String transactionId, String status, String paymentId) {}
