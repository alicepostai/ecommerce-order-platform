package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.port.in.*;
import com.ecommerce.orders.application.port.out.IdempotencyStore;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("IdempotencyKeyFilter — testes de comportamento")
class IdempotencyKeyFilterTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ORDER_ID    = UUID.randomUUID();

    static final String IDEMPOTENCY_KEY = "test-idem-key-001";
    static final String REQUEST_BODY    = "{\"customerId\":\"" + CUSTOMER_ID + "\"}";
    static final String REQUEST_HASH    = IdempotencyKeyFilter.sha256Hex(REQUEST_BODY.getBytes());

    @Autowired MockMvc mockMvc;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean IdempotencyStore idempotencyStore;
    @MockBean CreateOrderUseCase createOrder;
    @MockBean GetOrderUseCase getOrder;
    @MockBean ListCustomerOrdersUseCase listOrders;
    @MockBean AddOrderItemUseCase addItem;
    @MockBean RemoveOrderItemUseCase removeItem;
    @MockBean ConfirmOrderUseCase confirmOrder;
    @MockBean CancelOrderUseCase cancelOrder;

    // ── Cenário: sem header — filtro transparente ─────────────────────────────

    @Test
    @DisplayName("Sem Idempotency-Key, requisição prossegue normalmente e não consulta o store")
    void noHeader_doesNotInteractWithStore() throws Exception {
        when(createOrder.create(any())).thenReturn(order("CREATED"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated());

        verify(idempotencyStore, never()).find(any(), any());
        verify(idempotencyStore, never()).store(any(), any(), any(), anyInt(), any());
    }

    // ── Cenário: primeira chamada — armazena a resposta ──────────────────────

    @Test
    @DisplayName("Primeira chamada com Idempotency-Key persiste a resposta no store")
    void firstCall_storesResponse() throws Exception {
        when(idempotencyStore.find(eq(IDEMPOTENCY_KEY), any())).thenReturn(Optional.empty());
        when(createOrder.create(any())).thenReturn(order("CREATED"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .header(IdempotencyKeyFilter.IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated());

        verify(idempotencyStore).store(
                eq(IDEMPOTENCY_KEY), eq("POST:/api/v1/orders"),
                eq(REQUEST_HASH), eq(201), any());
    }

    // ── Cenário: replay com mesma chave e payload ────────────────────────────

    @Test
    @DisplayName("Replay com mesma Idempotency-Key e mesmo payload retorna resposta armazenada")
    void replay_sameKeyAndPayload_returnsCachedResponse() throws Exception {
        String storedBody = "{\"id\":\"" + ORDER_ID + "\",\"status\":\"CREATED\"}";
        var stored = new IdempotencyStore.StoredResponse(REQUEST_HASH, 201, storedBody);
        when(idempotencyStore.find(eq(IDEMPOTENCY_KEY), any())).thenReturn(Optional.of(stored));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .header(IdempotencyKeyFilter.IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        // Use case must NOT be called — response was replayed
        verify(createOrder, never()).create(any());
    }

    // ── Cenário: mesma chave com payload diferente → 422 ─────────────────────

    @Test
    @DisplayName("Mesma Idempotency-Key com payload diferente retorna 422 idempotency-key-mismatch")
    void mismatch_sameKeyDifferentPayload_returns422() throws Exception {
        String differentHash = IdempotencyKeyFilter.sha256Hex("{\"customerId\":\"other-customer\"}".getBytes());
        var stored = new IdempotencyStore.StoredResponse(differentHash, 201, "{}");
        when(idempotencyStore.find(eq(IDEMPOTENCY_KEY), any())).thenReturn(Optional.of(stored));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .header(IdempotencyKeyFilter.IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/idempotency-key-mismatch"))
                .andExpect(jsonPath("$.correlationId").exists());

        verify(createOrder, never()).create(any());
    }

    // ── Cenário: GET não é interceptado pelo filtro ───────────────────────────

    @Test
    @DisplayName("Requests GET nao sao interceptados (metodo nao e mutacao)")
    void getRequest_notIntercepted() throws Exception {
        when(getOrder.getById(any())).thenReturn(order("CREATED"));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read")))
                        .header(IdempotencyKeyFilter.IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY))
                .andExpect(status().isOk());

        verify(idempotencyStore, never()).find(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrderResult order(String status) {
        return new OrderResult(ORDER_ID, CUSTOMER_ID, status, List.of(), null, null, 0, null);
    }
}
