package com.ecommerce.orders.infrastructure.persistence.mapper;

import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentMapper")
class PaymentMapperTest {

    private static final Money AMOUNT = new Money(new BigDecimal("399.80"), "BRL");

    private PaymentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentMapper();
    }

    @Test
    @DisplayName("toEntity converte Payment para PaymentEntity corretamente")
    void toEntity() {
        var payment = Payment.create(PaymentId.generate(), OrderId.generate(), AMOUNT, 2);
        payment.pullDomainEvents();

        var entity = mapper.toEntity(payment);

        assertThat(entity.getId()).isEqualTo(payment.getId().value());
        assertThat(entity.getOrderId()).isEqualTo(payment.getOrderId().value());
        assertThat(entity.getAmount()).isEqualByComparingTo(AMOUNT.amount());
        assertThat(entity.getCurrency()).isEqualTo("BRL");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getAttemptNumber()).isEqualTo(2);
        assertThat(entity.getTransactionId()).isNull();
    }

    @Test
    @DisplayName("toDomain converte PaymentEntity para Payment corretamente")
    void toDomain() {
        var id = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var entity = new PaymentEntity(id, orderId, "APPROVED",
                new BigDecimal("399.80"), "BRL", 1);

        var domain = mapper.toDomain(entity);

        assertThat(domain.getId().value()).isEqualTo(id);
        assertThat(domain.getOrderId().value()).isEqualTo(orderId);
        assertThat(domain.getAmount()).isEqualTo(new Money(new BigDecimal("399.80"), "BRL"));
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(domain.getAttemptNumber()).isEqualTo(1);
        assertThat(domain.getTransactionId()).isNull();
        assertThat(domain.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("updateEntity atualiza status e transactionId da entity")
    void updateEntity() {
        var id = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var entity = new PaymentEntity(id, orderId, "PENDING",
                new BigDecimal("399.80"), "BRL", 1);
        var payment = Payment.reconstitute(
                new PaymentId(id), new OrderId(orderId), AMOUNT,
                PaymentStatus.REJECTED, 1, "tx-0002");

        mapper.updateEntity(entity, payment);

        assertThat(entity.getStatus()).isEqualTo("REJECTED");
        assertThat(entity.getTransactionId()).isEqualTo("tx-0002");
    }
}
