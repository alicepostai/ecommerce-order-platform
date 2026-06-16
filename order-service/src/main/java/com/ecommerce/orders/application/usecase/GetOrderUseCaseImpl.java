package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.port.in.GetOrderUseCase;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetOrderUseCaseImpl implements GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderUseCaseImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResult getById(UUID orderId) {
        return orderRepository.findById(new OrderId(orderId))
                .map(OrderResult::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
    }
}
