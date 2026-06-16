package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.ConfirmOrderCommand;
import com.ecommerce.orders.application.dto.OrderResult;
import com.ecommerce.orders.application.port.in.ConfirmOrderUseCase;
import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.application.port.out.NotificationPort;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.application.port.out.ProductCatalogGateway;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.ProductId;
import com.ecommerce.orders.domain.model.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class ConfirmOrderUseCaseImpl implements ConfirmOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductCatalogGateway catalogGateway;
    private final DomainEventPublisher eventPublisher;
    private final NotificationPort notificationPort;

    public ConfirmOrderUseCaseImpl(OrderRepository orderRepository,
                                   ProductCatalogGateway catalogGateway,
                                   DomainEventPublisher eventPublisher,
                                   NotificationPort notificationPort) {
        this.orderRepository = orderRepository;
        this.catalogGateway = catalogGateway;
        this.eventPublisher = eventPublisher;
        this.notificationPort = notificationPort;
    }

    @Override
    public OrderResult confirm(ConfirmOrderCommand command) {
        var orderId = new OrderId(command.orderId());
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.value().toString()));

        Map<ProductId, ProductSnapshot> snapshots = new HashMap<>();
        for (var item : order.getItems()) {
            snapshots.put(item.getProductId(), catalogGateway.fetchProduct(item.getProductId()));
        }

        order.confirm(snapshots);
        var events = order.pullDomainEvents();
        orderRepository.save(order);

        if (!events.isEmpty()) {
            eventPublisher.publishAll(events, "Order", order.getId().value());
            notificationPort.send(
                    order.getCustomerId(),
                    NotificationPort.NotificationTemplate.ORDER_CONFIRMED,
                    Map.of("orderId", order.getId().value().toString()));
        }

        return OrderResult.from(order);
    }
}
