package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.InitiatePaymentCommand;
import com.ecommerce.orders.application.dto.PaymentResult;
import com.ecommerce.orders.application.port.in.InitiatePaymentUseCase;
import com.ecommerce.orders.application.port.out.*;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class InitiatePaymentUseCaseImpl implements InitiatePaymentUseCase {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final NotificationPort notificationPort;
    private final DomainEventPublisher eventPublisher;

    public InitiatePaymentUseCaseImpl(OrderRepository orderRepository,
                                      PaymentRepository paymentRepository,
                                      PaymentGatewayPort paymentGateway,
                                      NotificationPort notificationPort,
                                      DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.notificationPort = notificationPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PaymentResult initiatePayment(InitiatePaymentCommand command) {
        var orderId = new OrderId(command.orderId());
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.value().toString()));

        // Validates CONFIRMED status and no pending payment; throws on violation
        order.initiatePayment();

        int attemptNumber = order.getPaymentAttempts() + 1;
        var paymentId = new PaymentId(UUID.randomUUID());
        var payment = Payment.create(paymentId, orderId, order.getTotal(), attemptNumber);

        paymentRepository.save(payment);
        orderRepository.save(order);

        // Call gateway synchronously
        var result = paymentGateway.charge(paymentId, orderId, order.getTotal(), command.cardToken());

        if (result.status() == PaymentGatewayPort.ChargeStatus.APPROVED) {
            payment.approve(result.transactionId());
            order.applyPaymentApproved();
            safeNotify(order.getCustomerId(), NotificationPort.NotificationTemplate.ORDER_PAID,
                    Map.of("orderId", orderId.value().toString()));
        } else {
            payment.reject(result.transactionId());
            order.applyPaymentRejected();
            if (order.getStatus() == OrderStatus.CANCELLED) {
                safeNotify(order.getCustomerId(), NotificationPort.NotificationTemplate.ORDER_CANCELLED,
                        Map.of("orderId", orderId.value().toString()));
            }
        }

        var orderEvents = order.pullDomainEvents();
        var paymentEvents = payment.pullDomainEvents();
        paymentRepository.save(payment);
        orderRepository.save(order);

        if (!orderEvents.isEmpty()) {
            eventPublisher.publishAll(orderEvents, "Order", orderId.value());
        }
        if (!paymentEvents.isEmpty()) {
            eventPublisher.publishAll(paymentEvents, "Payment", paymentId.value());
        }

        return PaymentResult.from(payment);
    }

    private void safeNotify(CustomerId recipientId, NotificationPort.NotificationTemplate template,
                             Map<String, String> payload) {
        try {
            notificationPort.send(recipientId, template, payload);
        } catch (Exception e) {
            // Notification failures are non-fatal; just log (observability in T17)
        }
    }
}
