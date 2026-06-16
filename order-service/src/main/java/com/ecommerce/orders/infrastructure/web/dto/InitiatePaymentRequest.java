package com.ecommerce.orders.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull @Valid PaymentMethodDto method) {

    public record PaymentMethodDto(
            @NotNull String type,
            @NotBlank String cardToken) {}
}
