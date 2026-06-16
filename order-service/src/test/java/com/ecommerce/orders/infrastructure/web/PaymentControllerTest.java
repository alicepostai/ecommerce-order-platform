package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.dto.PaymentResult;
import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.application.port.in.GetPaymentUseCase;
import com.ecommerce.orders.application.port.in.InitiatePaymentUseCase;
import com.ecommerce.orders.application.port.out.IdempotencyStore;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.exception.PaymentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController — testes de unidade (MockMvc)")
class PaymentControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    static final UUID ORDER_ID   = UUID.randomUUID();
    static final UUID PAYMENT_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean IdempotencyStore idempotencyStore;
    @MockBean InitiatePaymentUseCase initiatePayment;
    @MockBean GetPaymentUseCase getPayment;

    // ── POST /api/v1/payments ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /payments com token aprovado retorna 201 com status APPROVED")
    void initiatePayment_201_approved() throws Exception {
        when(initiatePayment.initiatePayment(any())).thenReturn(approvedPaymentResult());

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","method":{"type":"CARD","cardToken":"tok-approved"}}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.attemptNumber").value(1));
    }

    @Test
    @DisplayName("POST /payments para pedido nao-confirmado retorna 409 order-not-confirmed")
    void initiatePayment_409_orderNotConfirmed() throws Exception {
        when(initiatePayment.initiatePayment(any()))
                .thenThrow(new InvalidStateTransitionException("order-not-confirmed",
                        "Order is not in CONFIRMED status"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","method":{"type":"CARD","cardToken":"tok-approved"}}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/order-not-confirmed"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("POST /payments com pagamento duplicado retorna 409 payment-already-pending")
    void initiatePayment_409_paymentAlreadyPending() throws Exception {
        when(initiatePayment.initiatePayment(any()))
                .thenThrow(new InvalidStateTransitionException("payment-already-pending",
                        "A payment is already pending"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","method":{"type":"CARD","cardToken":"tok-approved"}}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/payment-already-pending"));
    }

    @Test
    @DisplayName("POST /payments com pedido nao encontrado retorna 404")
    void initiatePayment_404_orderNotFound() throws Exception {
        when(initiatePayment.initiatePayment(any()))
                .thenThrow(new OrderNotFoundException(ORDER_ID.toString()));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","method":{"type":"CARD","cardToken":"tok-approved"}}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/order-not-found"));
    }

    @Test
    @DisplayName("POST /payments com gateway indisponivel retorna 502")
    void initiatePayment_502_gatewayUnavailable() throws Exception {
        when(initiatePayment.initiatePayment(any()))
                .thenThrow(new ExternalServiceException("Payment gateway unavailable"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","method":{"type":"CARD","cardToken":"tok-unstable"}}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/external-service-unavailable"));
    }

    @Test
    @DisplayName("POST /payments sem escopo payments:write retorna 403")
    void initiatePayment_403_wrongScope() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","method":{"type":"CARD","cardToken":"tok-approved"}}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /payments com payload invalido (orderId ausente) retorna 400")
    void initiatePayment_400_invalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":{"type":"CARD","cardToken":"tok-approved"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/payments/{paymentId} ────────────────────────────────────

    @Test
    @DisplayName("GET /payments/{id} retorna 200 com dados do pagamento")
    void getPayment_200() throws Exception {
        when(getPayment.getById(PAYMENT_ID)).thenReturn(approvedPaymentResult());

        mockMvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()));
    }

    @Test
    @DisplayName("GET /payments/{id} para pagamento inexistente retorna 404")
    void getPayment_404() throws Exception {
        when(getPayment.getById(any()))
                .thenThrow(new PaymentNotFoundException(PAYMENT_ID.toString()));

        mockMvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/payment-not-found"));
    }

    @Test
    @DisplayName("GET /payments/{id} sem escopo payments:read retorna 403")
    void getPayment_403_wrongScope() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read"))))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PaymentResult approvedPaymentResult() {
        return new PaymentResult(
                PAYMENT_ID, ORDER_ID, "APPROVED",
                new BigDecimal("399.80"), "BRL", 1, "tx-0001");
    }
}
