package com.ecommerce.orders.infrastructure.persistence.adapter;

import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.domain.model.CustomerId;
import com.ecommerce.orders.domain.model.Order;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.infrastructure.persistence.mapper.OrderMapper;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaOrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderPersistenceAdapter implements OrderRepository {

    private final JpaOrderRepository jpaOrderRepository;
    private final OrderMapper orderMapper;

    public OrderPersistenceAdapter(JpaOrderRepository jpaOrderRepository, OrderMapper orderMapper) {
        this.jpaOrderRepository = jpaOrderRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public Order save(Order order) {
        var entity = jpaOrderRepository.findById(order.getId().value())
                .map(existing -> {
                    orderMapper.updateEntity(existing, order);
                    return existing;
                })
                .orElseGet(() -> orderMapper.toEntity(order));

        return orderMapper.toDomain(jpaOrderRepository.save(entity));
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaOrderRepository.findById(id.value())
                .map(orderMapper::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(CustomerId customerId) {
        return jpaOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId.value())
                .stream()
                .map(orderMapper::toDomain)
                .toList();
    }

    @Override
    public boolean hasActiveOrderForCustomer(CustomerId customerId) {
        return jpaOrderRepository.existsActiveOrderForCustomer(customerId.value());
    }
}
