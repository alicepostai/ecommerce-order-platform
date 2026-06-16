package com.ecommerce.orders.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentCallbackRequest(
        @NotBlank String eventId,
        @NotNull UUID paymentId,
        @NotBlank String status,
        @NotBlank String transactionId) {}
