package com.ecommerce.orders.infrastructure.persistence.adapter;

import com.ecommerce.orders.application.port.out.PaymentRepository;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.Payment;
import com.ecommerce.orders.domain.model.PaymentId;
import com.ecommerce.orders.infrastructure.persistence.mapper.PaymentMapper;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaPaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final JpaPaymentRepository jpaPaymentRepository;
    private final PaymentMapper paymentMapper;

    public PaymentPersistenceAdapter(JpaPaymentRepository jpaPaymentRepository, PaymentMapper paymentMapper) {
        this.jpaPaymentRepository = jpaPaymentRepository;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public Payment save(Payment payment) {
        var entity = jpaPaymentRepository.findById(payment.getId().value())
                .map(existing -> {
                    paymentMapper.updateEntity(existing, payment);
                    return existing;
                })
                .orElseGet(() -> paymentMapper.toEntity(payment));

        return paymentMapper.toDomain(jpaPaymentRepository.save(entity));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jpaPaymentRepository.findById(id.value())
                .map(paymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findPendingByOrderId(OrderId orderId) {
        return jpaPaymentRepository.findPendingByOrderId(orderId.value())
                .map(paymentMapper::toDomain);
    }

    @Override
    public List<Payment> findByOrderId(OrderId orderId) {
        return jpaPaymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId.value())
                .stream()
                .map(paymentMapper::toDomain)
                .toList();
    }
}
