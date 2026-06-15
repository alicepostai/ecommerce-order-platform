package com.ecommerce.orders.domain.model;

import com.ecommerce.orders.domain.event.*;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Payment {

    private final PaymentId id;
    private final OrderId orderId;
    private final Money amount;
    private PaymentStatus status;
    private final int attemptNumber;
    private String transactionId;
    private final List<DomainEvent> domainEvents;

    private Payment(PaymentId id, OrderId orderId, Money amount, PaymentStatus status,
                    int attemptNumber, String transactionId) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.attemptNumber = attemptNumber;
        this.transactionId = transactionId;
        this.domainEvents = new ArrayList<>();
    }

    public static Payment create(PaymentId id, OrderId orderId, Money amount, int attemptNumber) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (attemptNumber < 1)
            throw new IllegalArgumentException("attemptNumber must be >= 1");

        var payment = new Payment(id, orderId, amount, PaymentStatus.PENDING, attemptNumber, null);
        payment.registerEvent(new PaymentCreated(id, orderId, attemptNumber));
        return payment;
    }

    public static Payment reconstitute(PaymentId id, OrderId orderId, Money amount,
                                       PaymentStatus status, int attemptNumber, String transactionId) {
        return new Payment(id, orderId, amount, status, attemptNumber, transactionId);
    }

    // ── Comandos ─────────────────────────────────────────────────────────────

    public void approve(String transactionId) {
        if (status == PaymentStatus.APPROVED) return; // idempotente
        requireStatus(PaymentStatus.PENDING, "invalid-payment-state");
        this.status = PaymentStatus.APPROVED;
        this.transactionId = transactionId;
        registerEvent(new PaymentApproved(id, orderId));
    }

    public void reject(String transactionId) {
        requireStatus(PaymentStatus.PENDING, "invalid-payment-state");
        this.status = PaymentStatus.REJECTED;
        this.transactionId = transactionId;
        registerEvent(new PaymentRejected(id, orderId));
    }

    public void cancel() {
        if (status == PaymentStatus.CANCELLED) return; // idempotente
        if (status == PaymentStatus.APPROVED || status == PaymentStatus.REJECTED)
            throw new InvalidStateTransitionException("payment-not-cancellable",
                    "Cannot cancel a payment in status " + status);
        this.status = PaymentStatus.CANCELLED;
        registerEvent(new PaymentCancelled(id, orderId));
    }

    // ── Eventos de domínio ───────────────────────────────────────────────────

    public List<DomainEvent> pullDomainEvents() {
        var snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public PaymentId getId() { return id; }
    public OrderId getOrderId() { return orderId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getTransactionId() { return transactionId; }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private void requireStatus(PaymentStatus required, String errorCode) {
        if (status != required)
            throw new InvalidStateTransitionException(errorCode,
                    "Operation requires status %s but current status is %s".formatted(required, status));
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
