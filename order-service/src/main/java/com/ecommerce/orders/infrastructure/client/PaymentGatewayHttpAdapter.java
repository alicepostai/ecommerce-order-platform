package com.ecommerce.orders.infrastructure.client;

import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.application.port.out.PaymentGatewayPort;
import com.ecommerce.orders.domain.model.Money;
import com.ecommerce.orders.domain.model.OrderId;
import com.ecommerce.orders.domain.model.PaymentId;
import com.ecommerce.orders.infrastructure.client.dto.PaymentGatewayRequest;
import com.ecommerce.orders.infrastructure.client.dto.PaymentGatewayResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentGatewayHttpAdapter implements PaymentGatewayPort {

    private final RestClient restClient;

    public PaymentGatewayHttpAdapter(@Qualifier("paymentGatewayRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = "paymentGateway")
    @Retry(name = "paymentGateway")
    public ChargeResult charge(PaymentId paymentId, OrderId orderId, Money amount, String cardToken) {
        var request = new PaymentGatewayRequest(
                paymentId.value().toString(),
                orderId.value().toString(),
                new PaymentGatewayRequest.AmountDto(amount.amount(), amount.currency()),
                new PaymentGatewayRequest.MethodDto("CARD", cardToken));

        var response = restClient.post()
                .uri("/payments")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ExternalServiceException(
                            "Payment gateway error: " + res.getStatusCode().value());
                })
                .body(PaymentGatewayResponse.class);

        if (response == null) {
            throw new ExternalServiceException("Payment gateway returned empty response");
        }

        var status = "APPROVED".equalsIgnoreCase(response.status())
                ? ChargeStatus.APPROVED
                : ChargeStatus.REJECTED;

        return new ChargeResult(response.transactionId(), status);
    }
}
