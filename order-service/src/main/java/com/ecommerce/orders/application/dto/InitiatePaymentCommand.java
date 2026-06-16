package com.ecommerce.orders.application.dto;

import java.util.UUID;

public record InitiatePaymentCommand(UUID orderId, String cardToken) {}
