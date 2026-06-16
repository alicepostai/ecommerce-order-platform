package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.InitiatePaymentCommand;
import com.ecommerce.orders.application.dto.PaymentResult;
import com.ecommerce.orders.application.port.in.InitiatePaymentUseCase;
import com.ecommerce.orders.application.port.out.NotificationPort;
import com.ecommerce.orders.application.port.out.PaymentGatewayPort;
import com.ecommerce.orders.domain.model.CustomerId;
import com.ecommerce.orders.domain.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InitiatePaymentUseCaseImpl implements InitiatePaymentUseCase {

    private final PaymentTransactionHelper txHelper;
    private final PaymentGatewayPort paymentGateway;
    private final NotificationPort notificationPort;

    public InitiatePaymentUseCaseImpl(PaymentTransactionHelper txHelper,
                                      PaymentGatewayPort paymentGateway,
                                      NotificationPort notificationPort) {
        this.txHelper = txHelper;
        this.paymentGateway = paymentGateway;
        this.notificationPort = notificationPort;
    }

    @Override
    public PaymentResult initiatePayment(InitiatePaymentCommand command) {
        // Phase 1: validate + create PENDING — committed immediately (normative S4.8)
        var phase1 = txHelper.createPending(command.orderId());

        // Gateway call — outside any transaction; 502 leaves phase 1 committed
        var result = paymentGateway.charge(
                phase1.paymentId(), phase1.orderId(), phase1.amount(), command.cardToken());

        // Phase 2: apply result — committed immediately
        var phase2 = txHelper.applyResult(phase1, result);

        safeNotify(phase2.customerId(), phase2.finalOrderStatus(), phase1.orderId().value().toString());

        return PaymentResult.from(phase2.payment());
    }

    private void safeNotify(CustomerId customerId, OrderStatus finalStatus, String orderId) {
        try {
            var template = finalStatus == OrderStatus.PAID
                    ? NotificationPort.NotificationTemplate.ORDER_PAID
                    : (finalStatus == OrderStatus.CANCELLED
                            ? NotificationPort.NotificationTemplate.ORDER_CANCELLED
                            : null);
            if (template != null) {
                notificationPort.send(customerId, template, Map.of("orderId", orderId));
            }
        } catch (Exception e) {
        }
    }
}
