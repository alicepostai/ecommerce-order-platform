package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.application.port.out.PaymentGatewayPort;
import com.ecommerce.orders.application.port.out.PaymentRepository;
import com.ecommerce.orders.domain.event.DomainEvent;
import com.ecommerce.orders.domain.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles split transaction for payment initiation so that the PENDING state
 * is committed before the gateway call (normative S4.8: 502 leaves payment PENDING).
 */
@Service
public class PaymentTransactionHelper {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    public PaymentTransactionHelper(OrderRepository orderRepository,
                                    PaymentRepository paymentRepository,
                                    DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    public record Phase1Result(PaymentId paymentId, OrderId orderId, Money amount, int attemptNumber) {}

    public record Phase2Result(Payment payment, OrderStatus finalOrderStatus,
                               CustomerId customerId, List<DomainEvent> orderEvents,
                               List<DomainEvent> paymentEvents) {}

    /**
     * Phase 1: validates order state, creates Payment(PENDING), moves order to PAYMENT_PENDING.
     * Committed independently so that gateway failure leaves the PENDING state in DB.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Phase1Result createPending(UUID orderId) {
        var orderIdVO = new OrderId(orderId);
        var order = orderRepository.findById(orderIdVO)
                .orElseThrow(() -> new com.ecommerce.orders.domain.exception.OrderNotFoundException(orderId.toString()));

        order.initiatePayment(); // throws if not CONFIRMED or already PENDING

        int attemptNumber = order.getPaymentAttempts() + 1;
        var paymentId = new PaymentId(UUID.randomUUID());
        var payment = Payment.create(paymentId, orderIdVO, order.getTotal(), attemptNumber);

        paymentRepository.save(payment);
        orderRepository.save(order);

        // Publish PaymentInitiated event atomically with state change
        var orderEvents = order.pullDomainEvents();
        if (!orderEvents.isEmpty()) {
            eventPublisher.publishAll(orderEvents, "Order", orderId);
        }

        return new Phase1Result(paymentId, orderIdVO, order.getTotal(), attemptNumber);
    }

    /**
     * Phase 2: applies gateway result (APPROVED/REJECTED) to payment and order.
     * Committed independently. Notifications happen outside after this returns.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Phase2Result applyResult(Phase1Result phase1, PaymentGatewayPort.ChargeResult result) {
        var payment = paymentRepository.findById(phase1.paymentId()).orElseThrow();
        var order = orderRepository.findById(phase1.orderId()).orElseThrow();

        if (result.status() == PaymentGatewayPort.ChargeStatus.APPROVED) {
            payment.approve(result.transactionId());
            order.applyPaymentApproved();
        } else {
            payment.reject(result.transactionId());
            order.applyPaymentRejected();
        }

        var orderEvents = order.pullDomainEvents();
        var paymentEvents = payment.pullDomainEvents();

        paymentRepository.save(payment);
        orderRepository.save(order);

        if (!orderEvents.isEmpty()) {
            eventPublisher.publishAll(orderEvents, "Order", phase1.orderId().value());
        }
        if (!paymentEvents.isEmpty()) {
            eventPublisher.publishAll(paymentEvents, "Payment", phase1.paymentId().value());
        }

        return new Phase2Result(payment, order.getStatus(), order.getCustomerId(), orderEvents, paymentEvents);
    }
}
