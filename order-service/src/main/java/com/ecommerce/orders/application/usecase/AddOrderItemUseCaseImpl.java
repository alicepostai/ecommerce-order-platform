package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.AddOrderItemCommand;
import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.port.in.AddOrderItemUseCase;
import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.application.port.out.ProductCatalogGateway;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.OrderItemId;
import com.ecommerce.orders.domain.model.ProductId;
import com.ecommerce.orders.domain.model.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AddOrderItemUseCaseImpl implements AddOrderItemUseCase {

    private final OrderRepository orderRepository;
    private final ProductCatalogGateway catalogGateway;
    private final DomainEventPublisher eventPublisher;

    public AddOrderItemUseCaseImpl(OrderRepository orderRepository,
                                   ProductCatalogGateway catalogGateway,
                                   DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.catalogGateway = catalogGateway;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResult addItem(AddOrderItemCommand command) {
        var orderId = new OrderId(command.orderId());
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.value().toString()));

        var productId = new ProductId(command.productId());
        catalogGateway.fetchProduct(productId); // validates availability

        order.addItem(OrderItemId.generate(), productId, new Quantity(command.quantity()));
        orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents(), "Order", order.getId().value());

        return OrderResult.from(order);
    }
}
