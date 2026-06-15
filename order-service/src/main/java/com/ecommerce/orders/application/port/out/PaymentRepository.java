package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.Payment;
import com.ecommerce.orders.domain.model.PaymentId;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId id);
    Optional<Payment> findPendingByOrderId(OrderId orderId);
    List<Payment> findByOrderId(OrderId orderId);
}
