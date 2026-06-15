package com.ecommerce.orders.infrastructure.persistence.mapper;

import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(
                payment.getId().value(),
                payment.getOrderId().value(),
                payment.getCardToken(),
                payment.getStatus().name(),
                payment.getAttemptNumber()
        );
    }

    public void updateEntity(PaymentEntity entity, Payment payment) {
        entity.setStatus(payment.getStatus().name());
    }

    public Payment toDomain(PaymentEntity entity) {
        return Payment.reconstitute(
                new PaymentId(entity.getId()),
                new OrderId(entity.getOrderId()),
                entity.getCardToken(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getAttemptNumber()
        );
    }
}
