package com.ecommerce.orders.application.dto;

import com.ecommerce.orders.domain.model.Order;
import com.ecommerce.orders.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResult(
        UUID id,
        UUID customerId,
        String status,
        List<OrderItemResult> items,
        BigDecimal totalAmount,
        String totalCurrency,
        int paymentAttempts,
        String cancellationReason) {

    public record OrderItemResult(
            UUID id,
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String unitPriceCurrency) {

        static OrderItemResult from(OrderItem item) {
            return new OrderItemResult(
                    item.getId().value(),
                    item.getProductId().value(),
                    item.getProductName(),
                    item.getQuantity().value(),
                    item.getUnitPrice() != null ? item.getUnitPrice().amount() : null,
                    item.getUnitPrice() != null ? item.getUnitPrice().currency() : null);
        }
    }

    public static OrderResult from(Order order) {
        return new OrderResult(
                order.getId().value(),
                order.getCustomerId().value(),
                order.getStatus().name(),
                order.getItems().stream().map(OrderItemResult::from).toList(),
                order.getTotal() != null ? order.getTotal().amount() : null,
                order.getTotal() != null ? order.getTotal().currency() : null,
                order.getPaymentAttempts(),
                order.getCancellationReason() != null ? order.getCancellationReason().name() : null);
    }
}
