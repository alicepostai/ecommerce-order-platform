package com.ecommerce.orders.infrastructure.client;

import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.application.port.out.CustomerGateway;
import com.ecommerce.orders.domain.exception.CustomerBlockedException;
import com.ecommerce.orders.domain.exception.CustomerNotFoundException;
import com.ecommerce.orders.domain.model.CustomerId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomerHttpAdapter implements CustomerGateway {

    private final RestClient restClient;

    public CustomerHttpAdapter(@Qualifier("customerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void validateActiveCustomer(CustomerId customerId) {
        restClient.get()
                .uri("/customers/{id}", customerId.value())
                .retrieve()
                .onStatus(s -> s.value() == 422, (req, res) -> {
                    throw new CustomerBlockedException(customerId.value().toString());
                })
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new CustomerNotFoundException(customerId.value().toString());
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ExternalServiceException(
                            "Customer service error: " + res.getStatusCode().value());
                })
                .toBodilessEntity();
    }
}
