package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.ProcessPaymentCallbackCommand;
import com.ecommerce.orders.application.port.in.ProcessPaymentCallbackUseCase;
import com.ecommerce.orders.application.port.out.*;
import com.ecommerce.orders.domain.event.LatePaymentResultReceived;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.exception.PaymentNotFoundException;
import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.application.port.out.MetricsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ProcessPaymentCallbackUseCaseImpl implements ProcessPaymentCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentCallbackUseCaseImpl.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final WebhookEventStore webhookEventStore;
    private final DomainEventPublisher eventPublisher;
    private final NotificationPort notificationPort;
    private final MetricsPort metrics;

    public ProcessPaymentCallbackUseCaseImpl(PaymentRepository paymentRepository,
                                             OrderRepository orderRepository,
                                             WebhookEventStore webhookEventStore,
                                             DomainEventPublisher eventPublisher,
                                             NotificationPort notificationPort,
                                             MetricsPort metrics) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.webhookEventStore = webhookEventStore;
        this.eventPublisher = eventPublisher;
        this.notificationPort = notificationPort;
        this.metrics = metrics;
    }

    @Override
    public void process(ProcessPaymentCallbackCommand command) {
        // Idempotência por eventId
        if (webhookEventStore.isProcessed(command.eventId())) {
            return;
        }

        var paymentId = new PaymentId(command.paymentId());
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId().toString()));

        var order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(payment.getOrderId().value().toString()));

        // Webhook tardio: pedido ou pagamento já cancelado
        if (order.getStatus() == OrderStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.CANCELLED) {
            log.warn("Late webhook received: eventId={}, paymentId={}, orderId={}, webhookStatus={} — order/payment already cancelled",
                    command.eventId(), command.paymentId(), payment.getOrderId().value(), command.status());
            var lateEvent = new LatePaymentResultReceived(paymentId, payment.getOrderId(), command.status());
            eventPublisher.publishAll(List.of(lateEvent), "Payment", paymentId.value());
            webhookEventStore.markProcessed(command.eventId(), command.paymentId());
            return;
        }

        if ("APPROVED".equalsIgnoreCase(command.status())) {
            payment.approve(command.transactionId());
            order.applyPaymentApproved();
            safeNotify(order.getCustomerId(), NotificationPort.NotificationTemplate.ORDER_PAID,
                    Map.of("orderId", order.getId().value().toString()));
        } else {
            payment.reject(command.transactionId());
            order.applyPaymentRejected();
            metrics.incrementPaymentsRejected();
            if (order.getStatus() == OrderStatus.CANCELLED) {
                metrics.incrementOrdersAutoCancelled();
                safeNotify(order.getCustomerId(), NotificationPort.NotificationTemplate.ORDER_CANCELLED,
                        Map.of("orderId", order.getId().value().toString()));
            }
        }

        var orderEvents = order.pullDomainEvents();
        var paymentEvents = payment.pullDomainEvents();
        paymentRepository.save(payment);
        orderRepository.save(order);

        if (!orderEvents.isEmpty()) {
            eventPublisher.publishAll(orderEvents, "Order", order.getId().value());
        }
        if (!paymentEvents.isEmpty()) {
            eventPublisher.publishAll(paymentEvents, "Payment", paymentId.value());
        }

        webhookEventStore.markProcessed(command.eventId(), command.paymentId());
    }

    private void safeNotify(CustomerId recipientId, NotificationPort.NotificationTemplate template,
                             Map<String, String> payload) {
        try {
            notificationPort.send(recipientId, template, payload);
        } catch (Exception e) {
            log.warn("Notification failed for template={}: {}", template, e.getMessage());
        }
    }
}
