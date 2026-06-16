package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.port.in.ListCustomerOrdersUseCase;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.domain.model.CustomerId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListCustomerOrdersUseCaseImpl implements ListCustomerOrdersUseCase {

    private final OrderRepository orderRepository;

    public ListCustomerOrdersUseCaseImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public List<OrderResult> listByCustomer(UUID customerId) {
        return orderRepository.findByCustomerId(new CustomerId(customerId))
                .stream()
                .map(OrderResult::from)
                .toList();
    }
}
