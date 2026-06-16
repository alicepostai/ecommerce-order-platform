package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.CancelOrderCommand;
import com.ecommerce.orders.application.port.in.CancelOrderUseCase;
import com.ecommerce.orders.application.port.out.DomainEventPublisher;
import com.ecommerce.orders.application.port.out.NotificationPort;
import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.application.port.out.PaymentRepository;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.model.CancellationReason;
import com.ecommerce.orders.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class CancelOrderUseCaseImpl implements CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;
    private final NotificationPort notificationPort;

    public CancelOrderUseCaseImpl(OrderRepository orderRepository,
                                  PaymentRepository paymentRepository,
                                  DomainEventPublisher eventPublisher,
                                  NotificationPort notificationPort) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.notificationPort = notificationPort;
    }

    @Override
    public void cancel(CancelOrderCommand command) {
        var orderId = new OrderId(command.orderId());
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.value().toString()));

        order.cancel(CancellationReason.CUSTOMER_REQUEST);

        paymentRepository.findPendingByOrderId(orderId).ifPresent(payment -> {
            payment.cancel();
            paymentRepository.save(payment);
        });

        orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents(), "Order", order.getId().value());

        notificationPort.send(
                order.getCustomerId(),
                NotificationPort.NotificationTemplate.ORDER_CANCELLED,
                Map.of("orderId", order.getId().value().toString()));
    }
}
