package com.ecommerce.orders.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${external.customer.base-url}")
    private String customerBaseUrl;

    @Value("${external.catalog.base-url}")
    private String catalogBaseUrl;

    @Value("${external.payment-gateway.base-url}")
    private String paymentGatewayBaseUrl;

    @Value("${external.notification.base-url}")
    private String notificationBaseUrl;

    @Bean("customerRestClient")
    public RestClient customerRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory())
                .baseUrl(customerBaseUrl)
                .build();
    }

    @Bean("catalogRestClient")
    public RestClient catalogRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory())
                .baseUrl(catalogBaseUrl)
                .build();
    }

    @Bean("paymentGatewayRestClient")
    public RestClient paymentGatewayRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory())
                .baseUrl(paymentGatewayBaseUrl)
                .build();
    }

    @Bean("notificationRestClient")
    public RestClient notificationRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory())
                .baseUrl(notificationBaseUrl)
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
