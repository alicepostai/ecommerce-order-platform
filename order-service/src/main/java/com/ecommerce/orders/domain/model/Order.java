package com.ecommerce.orders.domain.model;

import com.ecommerce.orders.domain.event.*;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;
import com.ecommerce.orders.domain.exception.OrderItemNotFoundException;

import java.util.*;

public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Money total;
    private int paymentAttempts;
    private CancellationReason cancellationReason;
    private final List<DomainEvent> domainEvents;

    private Order(OrderId id, CustomerId customerId) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.CREATED;
        this.items = new ArrayList<>();
        this.paymentAttempts = 0;
        this.domainEvents = new ArrayList<>();
    }

    public static Order create(OrderId id, CustomerId customerId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        var order = new Order(id, customerId);
        order.registerEvent(new OrderCreated(id, customerId));
        return order;
    }

    public static Order reconstitute(OrderId id, CustomerId customerId, OrderStatus status,
                                     List<OrderItem> items, Money total,
                                     int paymentAttempts, CancellationReason cancellationReason) {
        var order = new Order(id, customerId);
        order.status = status;
        order.items.addAll(items);
        order.total = total;
        order.paymentAttempts = paymentAttempts;
        order.cancellationReason = cancellationReason;
        return order;
    }

    public void addItem(OrderItemId itemId, ProductId productId, Quantity quantity) {
        requireStatus(OrderStatus.CREATED, "order-not-modifiable");
        items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.incrementQuantity(quantity),
                        () -> items.add(new OrderItem(itemId, productId, quantity))
                );
        registerEvent(new OrderItemAdded(id, productId, quantity));
    }

    public void removeItem(OrderItemId itemId) {
        requireStatus(OrderStatus.CREATED, "order-not-modifiable");
        var item = findItemById(itemId);
        items.remove(item);
        registerEvent(new OrderItemRemoved(id, itemId));
    }

    public void confirm(Map<ProductId, ProductSnapshot> snapshots) {
        if (status == OrderStatus.CONFIRMED) return;
        requireStatus(OrderStatus.CREATED, "order-not-confirmable");
        if (items.isEmpty())
            throw new InvalidStateTransitionException("empty-order", "Order has no items to confirm");

        for (var item : items) {
            var snapshot = snapshots.get(item.getProductId());
            if (snapshot == null)
                throw new InvalidStateTransitionException("product-snapshot-missing",
                        "No price snapshot provided for product: " + item.getProductId());
            item.applySnapshot(snapshot);
        }

        this.total = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(Money::add)
                .orElseThrow();

        this.status = OrderStatus.CONFIRMED;
        registerEvent(new OrderConfirmed(id, total));
    }

    public void cancel(CancellationReason reason) {
        if (status == OrderStatus.PAID)
            throw new InvalidStateTransitionException("order-not-cancellable", "Cannot cancel a paid order");
        if (status == OrderStatus.CANCELLED)
            throw new InvalidStateTransitionException("order-not-cancellable", "Order is already cancelled");
        this.cancellationReason = reason;
        this.status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelled(id, reason));
    }

    public void initiatePayment() {
        if (status == OrderStatus.PAYMENT_PENDING)
            throw new InvalidStateTransitionException("payment-already-pending",
                    "A payment is already pending for this order");
        requireStatus(OrderStatus.CONFIRMED, "order-not-confirmed");
        this.status = OrderStatus.PAYMENT_PENDING;
        registerEvent(new PaymentInitiated(id));
    }

    public void applyPaymentApproved() {
        if (status == OrderStatus.PAID) return;
        requireStatus(OrderStatus.PAYMENT_PENDING, "invalid-payment-state");
        this.status = OrderStatus.PAID;
        registerEvent(new OrderPaid(id));
    }

    public void applyPaymentRejected() {
        requireStatus(OrderStatus.PAYMENT_PENDING, "invalid-payment-state");
        this.paymentAttempts++;
        if (paymentAttempts >= 3) {
            this.status = OrderStatus.CANCELLED;
            this.cancellationReason = CancellationReason.PAYMENT_ATTEMPTS_EXCEEDED;
            registerEvent(new OrderCancelled(id, CancellationReason.PAYMENT_ATTEMPTS_EXCEEDED));
        } else {
            this.status = OrderStatus.CONFIRMED;
        }
    }

    public List<DomainEvent> pullDomainEvents() {
        var snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    public OrderId getId() { return id; }
    public CustomerId getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public Money getTotal() { return total; }
    public int getPaymentAttempts() { return paymentAttempts; }
    public CancellationReason getCancellationReason() { return cancellationReason; }

    private void requireStatus(OrderStatus required, String errorCode) {
        if (status != required)
            throw new InvalidStateTransitionException(errorCode,
                    "Operation requires status %s but current status is %s".formatted(required, status));
    }

    private OrderItem findItemById(OrderItemId itemId) {
        return items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
