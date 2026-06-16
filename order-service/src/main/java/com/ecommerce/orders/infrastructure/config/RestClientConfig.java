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
                .requestFactory(requestFactory(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                .baseUrl(customerBaseUrl)
                .build();
    }

    @Bean("catalogRestClient")
    public RestClient catalogRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                .baseUrl(catalogBaseUrl)
                .build();
    }

    @Bean("paymentGatewayRestClient")
    public RestClient paymentGatewayRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                .baseUrl(paymentGatewayBaseUrl)
                .build();
    }

    @Bean("notificationRestClient")
    public RestClient notificationRestClient(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(requestFactory(Duration.ofSeconds(1), Duration.ofSeconds(3)))
                .baseUrl(notificationBaseUrl)
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
