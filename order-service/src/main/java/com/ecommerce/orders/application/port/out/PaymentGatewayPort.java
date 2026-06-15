package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.model.Money;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.PaymentId;

public interface PaymentGatewayPort {

    enum ChargeStatus { APPROVED, REJECTED }

    record ChargeResult(String transactionId, ChargeStatus status) {}

    ChargeResult charge(PaymentId paymentId, OrderId orderId, Money amount, String cardToken);
}
