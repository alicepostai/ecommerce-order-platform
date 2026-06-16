package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.dto.RemoveOrderItemCommand;
import com.ecommerce.orders.application.port.in.RemoveOrderItemUseCase;
import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.OrderItemId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RemoveOrderItemUseCaseImpl implements RemoveOrderItemUseCase {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public RemoveOrderItemUseCaseImpl(OrderRepository orderRepository,
                                      DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResult removeItem(RemoveOrderItemCommand command) {
        var orderId = new OrderId(command.orderId());
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.value().toString()));

        order.removeItem(new OrderItemId(command.itemId()));
        orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents(), "Order", order.getId().value());

        return OrderResult.from(order);
    }
}
