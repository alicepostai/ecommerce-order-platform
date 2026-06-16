package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.CreateOrderCommand;
import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.port.in.CreateOrderUseCase;
import com.ecommerce.orders.application.port.out.CustomerGateway;
import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.domain.exception.ActiveOrderExistsException;
import com.ecommerce.orders.domain.model.CustomerId;
import com.ecommerce.orders.domain.model.Order;
import com.ecommerce.orders.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateOrderUseCaseImpl implements CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final CustomerGateway customerGateway;
    private final DomainEventPublisher eventPublisher;

    public CreateOrderUseCaseImpl(OrderRepository orderRepository,
                                  CustomerGateway customerGateway,
                                  DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.customerGateway = customerGateway;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResult create(CreateOrderCommand command) {
        var customerId = new CustomerId(command.customerId());

        customerGateway.validateActiveCustomer(customerId);

        if (orderRepository.hasActiveOrderForCustomer(customerId)) {
            throw new ActiveOrderExistsException(customerId.value().toString());
        }

        var order = Order.create(OrderId.generate(), customerId);
        orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents(), "Order", order.getId().value());

        return OrderResult.from(order);
    }
}
