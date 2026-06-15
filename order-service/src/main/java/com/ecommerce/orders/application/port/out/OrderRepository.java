package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.model.CustomerId;
import com.ecommerce.orders.domain.model.Order;
import com.ecommerce.orders.domain.model.OrderId;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomerId(CustomerId customerId);
    boolean hasActiveOrderForCustomer(CustomerId customerId);
}
