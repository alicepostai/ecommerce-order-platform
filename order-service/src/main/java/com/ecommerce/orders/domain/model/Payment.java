package com.ecommerce.orders.domain.model;

import com.ecommerce.orders.domain.event.*;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Payment {

    private final PaymentId id;
    private final OrderId orderId;
    private final String cardToken;
    private PaymentStatus status;
    private final int attemptNumber;
    private final List<DomainEvent> domainEvents;

    private Payment(PaymentId id, OrderId orderId, String cardToken, PaymentStatus status, int attemptNumber) {
        this.id = id;
        this.orderId = orderId;
        this.cardToken = cardToken;
        this.status = status;
        this.attemptNumber = attemptNumber;
        this.domainEvents = new ArrayList<>();
    }

    public static Payment create(PaymentId id, OrderId orderId, String cardToken, int attemptNumber) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (cardToken == null || cardToken.isBlank())
            throw new IllegalArgumentException("cardToken must not be null or blank");
        if (attemptNumber < 1)
            throw new IllegalArgumentException("attemptNumber must be >= 1");

        var payment = new Payment(id, orderId, cardToken, PaymentStatus.PENDING, attemptNumber);
        payment.registerEvent(new PaymentCreated(id, orderId, attemptNumber));
        return payment;
    }

    /** Reconstitui um pagamento já persistido sem disparar eventos de domínio. */
    public static Payment reconstitute(PaymentId id, OrderId orderId, String cardToken,
                                       PaymentStatus status, int attemptNumber) {
        return new Payment(id, orderId, cardToken, status, attemptNumber);
    }

    // ── Comandos ─────────────────────────────────────────────────────────────

    public void approve() {
        if (status == PaymentStatus.APPROVED) return; // idempotente
        requireStatus(PaymentStatus.PENDING, "invalid-payment-state");
        this.status = PaymentStatus.APPROVED;
        registerEvent(new PaymentApproved(id, orderId));
    }

    public void reject() {
        requireStatus(PaymentStatus.PENDING, "invalid-payment-state");
        this.status = PaymentStatus.REJECTED;
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
    public String getCardToken() { return cardToken; }
    public PaymentStatus getStatus() { return status; }
    public int getAttemptNumber() { return attemptNumber; }

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
