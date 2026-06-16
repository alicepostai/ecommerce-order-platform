package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.dto.*;
import com.ecommerce.orders.application.port.in.*;
import com.ecommerce.orders.application.port.out.IdempotencyStore;
import com.ecommerce.orders.domain.exception.*;
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

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController — testes de unidade (MockMvc)")
class OrderControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ORDER_ID    = UUID.randomUUID();
    static final UUID PRODUCT_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID ITEM_ID     = UUID.randomUUID();

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

    // ── POST /api/v1/orders ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /orders com cliente ativo retorna 201 + Location")
    void createOrder_201() throws Exception {
        when(createOrder.create(any())).thenReturn(createdOrderResult());

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()));
    }

    @Test
    @DisplayName("POST /orders com token invalido retorna 401")
    void createOrder_401_invalidToken() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Token is invalid"));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /orders com escopo errado (orders:read) retorna 403")
    void createOrder_403_wrongScope() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /orders com cliente bloqueado retorna 422 problem+json")
    void createOrder_422_customerBlocked() throws Exception {
        when(createOrder.create(any())).thenThrow(new CustomerBlockedException(CUSTOMER_ID.toString()));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/customer-blocked"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("POST /orders com pedido ativo existente retorna 409")
    void createOrder_409_activeOrderExists() throws Exception {
        when(createOrder.create(any())).thenThrow(new ActiveOrderExistsException(CUSTOMER_ID.toString()));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/active-order-exists"));
    }

    @Test
    @DisplayName("POST /orders com payload invalido retorna 400")
    void createOrder_400_invalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    // ── GET /api/v1/orders/{orderId} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /orders/{id} retorna 200 com pedido")
    void getOrder_200() throws Exception {
        when(getOrder.getById(ORDER_ID)).thenReturn(createdOrderResult());

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()));
    }

    @Test
    @DisplayName("GET /orders/{id} para pedido inexistente retorna 404")
    void getOrder_404() throws Exception {
        when(getOrder.getById(any())).thenThrow(new OrderNotFoundException(ORDER_ID.toString()));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/order-not-found"));
    }

    // ── GET /api/v1/orders?customerId= ───────────────────────────────────────

    @Test
    @DisplayName("GET /orders?customerId= retorna 200 com lista")
    void listOrders_200() throws Exception {
        when(listOrders.listByCustomer(CUSTOMER_ID)).thenReturn(List.of(createdOrderResult()));

        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", CUSTOMER_ID.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ORDER_ID.toString()));
    }

    // ── POST /api/v1/orders/{orderId}/items ──────────────────────────────────

    @Test
    @DisplayName("POST /orders/{id}/items com produto disponivel retorna 200")
    void addItem_200() throws Exception {
        when(addItem.addItem(any())).thenReturn(createdOrderResult());

        mockMvc.perform(post("/api/v1/orders/{id}/items", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /orders/{id}/items com quantidade invalida retorna 400")
    void addItem_400_invalidQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/items", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /orders/{id}/items com produto indisponivel retorna 422")
    void addItem_422_productUnavailable() throws Exception {
        when(addItem.addItem(any())).thenThrow(new ProductUnavailableException(PRODUCT_ID.toString()));

        mockMvc.perform(post("/api/v1/orders/{id}/items", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/product-unavailable"));
    }

    @Test
    @DisplayName("POST /orders/{id}/items em pedido confirmado retorna 409 order-not-modifiable")
    void addItem_409_orderNotModifiable() throws Exception {
        when(addItem.addItem(any())).thenThrow(
                new InvalidStateTransitionException("order-not-modifiable", "Order is confirmed"));

        mockMvc.perform(post("/api/v1/orders/{id}/items", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/order-not-modifiable"));
    }

    // ── DELETE /api/v1/orders/{orderId}/items/{itemId} ────────────────────────

    @Test
    @DisplayName("DELETE /orders/{id}/items/{itemId} retorna 200")
    void removeItem_200() throws Exception {
        when(removeItem.removeItem(any())).thenReturn(createdOrderResult());

        mockMvc.perform(delete("/api/v1/orders/{id}/items/{itemId}", ORDER_ID, ITEM_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /orders/{id}/items/{itemId} para item inexistente retorna 404")
    void removeItem_404_itemNotFound() throws Exception {
        when(removeItem.removeItem(any())).thenThrow(new OrderItemNotFoundException(
                new com.ecommerce.orders.domain.model.OrderItemId(ITEM_ID)));

        mockMvc.perform(delete("/api/v1/orders/{id}/items/{itemId}", ORDER_ID, ITEM_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/item-not-found"));
    }

    // ── POST /api/v1/orders/{orderId}/confirm ─────────────────────────────────

    @Test
    @DisplayName("POST /orders/{id}/confirm retorna 200 com CONFIRMED")
    void confirm_200() throws Exception {
        when(confirmOrder.confirm(any())).thenReturn(confirmedOrderResult());

        mockMvc.perform(post("/api/v1/orders/{id}/confirm", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(199.90));
    }

    @Test
    @DisplayName("POST /orders/{id}/confirm em pedido vazio retorna 422 empty-order")
    void confirm_422_emptyOrder() throws Exception {
        when(confirmOrder.confirm(any())).thenThrow(
                new InvalidStateTransitionException("empty-order", "Order has no items to confirm"));

        mockMvc.perform(post("/api/v1/orders/{id}/confirm", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/empty-order"));
    }

    // ── DELETE /api/v1/orders/{orderId} ──────────────────────────────────────

    @Test
    @DisplayName("DELETE /orders/{id} retorna 204")
    void cancelOrder_204() throws Exception {
        doNothing().when(cancelOrder).cancel(any());

        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /orders/{id} em pedido PAID retorna 409 order-not-cancellable")
    void cancelOrder_409_notCancellable() throws Exception {
        doThrow(new InvalidStateTransitionException("order-not-cancellable", "Cannot cancel a paid order"))
                .when(cancelOrder).cancel(any());

        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/order-not-cancellable"));
    }

    @Test
    @DisplayName("DELETE /orders/{id} para pedido inexistente retorna 404")
    void cancelOrder_404() throws Exception {
        doThrow(new OrderNotFoundException(ORDER_ID.toString())).when(cancelOrder).cancel(any());

        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrderResult createdOrderResult() {
        return new OrderResult(ORDER_ID, CUSTOMER_ID, "CREATED", List.of(), null, null, 0, null);
    }

    private OrderResult confirmedOrderResult() {
        var item = new OrderResult.OrderItemResult(
                ITEM_ID, PRODUCT_ID, "Teclado Mecânico TKL", 1,
                new BigDecimal("199.90"), "BRL");
        return new OrderResult(ORDER_ID, CUSTOMER_ID, "CONFIRMED", List.of(item),
                new BigDecimal("199.90"), "BRL", 0, null);
    }
}
