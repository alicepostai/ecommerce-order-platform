package com.ecommerce.orders.infrastructure.persistence.mapper;

import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.entity.OrderEntity;
import com.ecommerce.orders.infrastructure.persistence.entity.OrderItemEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderEntity toEntity(Order order) {
        var entity = new OrderEntity(
                order.getId().value(),
                order.getCustomerId().value(),
                order.getStatus().name(),
                order.getTotal() != null ? order.getTotal().amount() : null,
                order.getTotal() != null ? order.getTotal().currency() : null,
                order.getPaymentAttempts(),
                order.getCancellationReason() != null ? order.getCancellationReason().name() : null
        );
        for (var item : order.getItems()) {
            entity.getItems().add(toItemEntity(item, entity));
        }
        return entity;
    }

    public void updateEntity(OrderEntity entity, Order order) {
        entity.setStatus(order.getStatus().name());
        entity.setPaymentAttempts(order.getPaymentAttempts());
        entity.setCancellationReason(
                order.getCancellationReason() != null ? order.getCancellationReason().name() : null);
        if (order.getTotal() != null) {
            entity.setTotalAmount(order.getTotal().amount());
            entity.setTotalCurrency(order.getTotal().currency());
        }

        var existingIds = entity.getItems().stream()
                .map(OrderItemEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        var domainIds = order.getItems().stream()
                .map(i -> i.getId().value())
                .collect(java.util.stream.Collectors.toSet());

        entity.getItems().removeIf(i -> !domainIds.contains(i.getId()));

        for (var item : order.getItems()) {
            if (existingIds.contains(item.getId().value())) {
                entity.getItems().stream()
                        .filter(e -> e.getId().equals(item.getId().value()))
                        .findFirst()
                        .ifPresent(e -> {
                            e.setQuantity(item.getQuantity().value());
                            e.setProductName(item.getProductName());
                            if (item.getUnitPrice() != null) {
                                e.setUnitPriceAmount(item.getUnitPrice().amount());
                                e.setUnitPriceCurrency(item.getUnitPrice().currency());
                            }
                        });
            } else {
                entity.getItems().add(toItemEntity(item, entity));
            }
        }
    }

    public Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toItemDomain)
                .toList();

        Money total = entity.getTotalAmount() != null
                ? new Money(entity.getTotalAmount(), entity.getTotalCurrency())
                : null;

        CancellationReason reason = entity.getCancellationReason() != null
                ? CancellationReason.valueOf(entity.getCancellationReason())
                : null;

        return Order.reconstitute(
                new OrderId(entity.getId()),
                new CustomerId(entity.getCustomerId()),
                OrderStatus.valueOf(entity.getStatus()),
                items,
                total,
                entity.getPaymentAttempts(),
                reason
        );
    }

    private OrderItemEntity toItemEntity(OrderItem item, OrderEntity orderEntity) {
        return new OrderItemEntity(
                item.getId().value(),
                orderEntity,
                item.getProductId().value(),
                item.getProductName(),
                item.getQuantity().value(),
                item.getUnitPrice() != null ? item.getUnitPrice().amount() : null,
                item.getUnitPrice() != null ? item.getUnitPrice().currency() : null
        );
    }

    private OrderItem toItemDomain(OrderItemEntity entity) {
        Money unitPrice = entity.getUnitPriceAmount() != null
                ? new Money(entity.getUnitPriceAmount(), entity.getUnitPriceCurrency())
                : null;
        return OrderItem.reconstitute(
                new OrderItemId(entity.getId()),
                new ProductId(entity.getProductId()),
                entity.getProductName(),
                new Quantity(entity.getQuantity()),
                unitPrice
        );
    }
}
