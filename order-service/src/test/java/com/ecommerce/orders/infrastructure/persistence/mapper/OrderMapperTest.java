package com.ecommerce.orders.infrastructure.persistence.mapper;

import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.entity.OrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderMapper")
class OrderMapperTest {

    private OrderMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrderMapper();
    }

    @Test
    @DisplayName("toDomain converte OrderEntity para Order preservando campos")
    void toDomainPreservesFields() {
        var entityId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var entity = new OrderEntity(entityId, customerId, "CONFIRMED",
                new BigDecimal("299.80"), "BRL", 1, null);

        var domain = mapper.toDomain(entity);

        assertThat(domain.getId().value()).isEqualTo(entityId);
        assertThat(domain.getCustomerId().value()).isEqualTo(customerId);
        assertThat(domain.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(domain.getTotal()).isEqualTo(new Money(new BigDecimal("299.80"), "BRL"));
        assertThat(domain.getPaymentAttempts()).isEqualTo(1);
        assertThat(domain.getCancellationReason()).isNull();
    }

    @Test
    @DisplayName("toDomain com cancellationReason converte enum corretamente")
    void toDomainWithCancellationReason() {
        var entity = new OrderEntity(UUID.randomUUID(), UUID.randomUUID(), "CANCELLED",
                null, null, 3, "PAYMENT_ATTEMPTS_EXCEEDED");

        var domain = mapper.toDomain(entity);

        assertThat(domain.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(domain.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_ATTEMPTS_EXCEEDED);
        assertThat(domain.getTotal()).isNull();
    }

    @Test
    @DisplayName("toEntity converte Order para OrderEntity com status e itens")
    void toEntityPreservesFields() {
        var order = Order.create(OrderId.generate(), CustomerId.generate());
        order.addItem(OrderItemId.generate(), ProductId.generate(), Quantity.of(2));
        order.pullDomainEvents();

        var entity = mapper.toEntity(order);

        assertThat(entity.getId()).isEqualTo(order.getId().value());
        assertThat(entity.getCustomerId()).isEqualTo(order.getCustomerId().value());
        assertThat(entity.getStatus()).isEqualTo("CREATED");
        assertThat(entity.getPaymentAttempts()).isEqualTo(0);
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateEntity sincroniza status, paymentAttempts e cancellationReason")
    void updateEntitySyncsFields() {
        var orderId = OrderId.generate();
        var customerId = CustomerId.generate();

        var order = Order.create(orderId, customerId);
        var productId = ProductId.generate();
        order.addItem(OrderItemId.generate(), productId, Quantity.of(1));
        var snapshot = new ProductSnapshot(productId, "Produto X", Money.of("100.00", "BRL"));
        order.confirm(java.util.Map.of(productId, snapshot));
        order.initiatePayment();
        order.applyPaymentRejected(); // 1a rejeicao -> CONFIRMED
        order.initiatePayment();
        order.applyPaymentRejected(); // 2a rejeicao -> CONFIRMED
        order.initiatePayment();
        order.applyPaymentRejected(); // 3a rejeicao -> CANCELLED (auto-cancel)

        var entity = mapper.toEntity(order);
        var updated = mapper.toDomain(entity);

        mapper.updateEntity(entity, updated);

        assertThat(entity.getStatus()).isEqualTo("CANCELLED");
        assertThat(entity.getPaymentAttempts()).isEqualTo(3);
        assertThat(entity.getCancellationReason()).isEqualTo("PAYMENT_ATTEMPTS_EXCEEDED");
    }
}
