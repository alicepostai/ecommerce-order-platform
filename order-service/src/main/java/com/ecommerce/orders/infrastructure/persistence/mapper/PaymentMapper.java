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
                payment.getStatus().name(),
                payment.getAmount().amount(),
                payment.getAmount().currency(),
                payment.getAttemptNumber()
        );
    }

    public void updateEntity(PaymentEntity entity, Payment payment) {
        entity.setStatus(payment.getStatus().name());
        entity.setTransactionId(payment.getTransactionId());
    }

    public Payment toDomain(PaymentEntity entity) {
        return Payment.reconstitute(
                new PaymentId(entity.getId()),
                new OrderId(entity.getOrderId()),
                new Money(entity.getAmount(), entity.getCurrency()),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getAttemptNumber(),
                entity.getTransactionId()
        );
    }
}
