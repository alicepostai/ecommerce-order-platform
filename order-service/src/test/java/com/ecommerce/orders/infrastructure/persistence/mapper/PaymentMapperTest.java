package com.ecommerce.orders.infrastructure.persistence.mapper;

import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentMapper")
class PaymentMapperTest {

    private PaymentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentMapper();
    }

    @Test
    @DisplayName("toEntity converte Payment para PaymentEntity corretamente")
    void toEntity() {
        var payment = Payment.create(PaymentId.generate(), OrderId.generate(), "tok-test", 2);
        payment.pullDomainEvents();

        var entity = mapper.toEntity(payment);

        assertThat(entity.getId()).isEqualTo(payment.getId().value());
        assertThat(entity.getOrderId()).isEqualTo(payment.getOrderId().value());
        assertThat(entity.getCardToken()).isEqualTo("tok-test");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getAttemptNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("toDomain converte PaymentEntity para Payment corretamente")
    void toDomain() {
        var id = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var entity = new PaymentEntity(id, orderId, "tok-test", "APPROVED", 1);

        var domain = mapper.toDomain(entity);

        assertThat(domain.getId().value()).isEqualTo(id);
        assertThat(domain.getOrderId().value()).isEqualTo(orderId);
        assertThat(domain.getCardToken()).isEqualTo("tok-test");
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(domain.getAttemptNumber()).isEqualTo(1);
        assertThat(domain.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("updateEntity atualiza o status da entity")
    void updateEntity() {
        var id = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var entity = new PaymentEntity(id, orderId, "tok-test", "PENDING", 1);
        var payment = Payment.reconstitute(
                new PaymentId(id), new OrderId(orderId), "tok-test", PaymentStatus.REJECTED, 1);

        mapper.updateEntity(entity, payment);

        assertThat(entity.getStatus()).isEqualTo("REJECTED");
    }
}
