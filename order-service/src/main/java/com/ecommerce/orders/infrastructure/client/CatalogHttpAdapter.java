package com.ecommerce.orders.infrastructure.client;

import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.application.port.out.ProductCatalogGateway;
import com.ecommerce.orders.domain.exception.ProductNotFoundException;
import com.ecommerce.orders.domain.exception.ProductUnavailableException;
import com.ecommerce.orders.domain.model.Money;
import com.ecommerce.orders.domain.model.ProductId;
import com.ecommerce.orders.domain.model.ProductSnapshot;
import com.ecommerce.orders.infrastructure.client.dto.ProductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class CatalogHttpAdapter implements ProductCatalogGateway {

    private final RestClient restClient;

    public CatalogHttpAdapter(@Qualifier("catalogRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService")
    public ProductSnapshot fetchProduct(ProductId productId) {
        var response = restClient.get()
                .uri("/products/{id}", productId.value())
                .retrieve()
                .onStatus(s -> s.value() == 422, (req, res) -> {
                    throw new ProductUnavailableException(productId.value().toString());
                })
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new ProductNotFoundException(productId.value().toString());
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ExternalServiceException(
                            "Catalog service error: " + res.getStatusCode().value());
                })
                .body(ProductResponse.class);

        if (response == null) {
            throw new ExternalServiceException("Catalog service returned empty response");
        }

        return new ProductSnapshot(
                new ProductId(UUID.fromString(response.id())),
                response.name(),
                new Money(response.price().amount(), response.price().currency()));
    }
}
