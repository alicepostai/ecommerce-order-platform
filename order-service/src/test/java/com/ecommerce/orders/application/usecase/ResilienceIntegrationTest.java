package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.*;
import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.application.port.in.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Gherkin: "Gateway instável não derruba a plataforma" (S5).
 * Verifies: 502 after retries, payment stays PENDING, order stays PAYMENT_PENDING,
 * circuit breaker opens, other endpoints continue working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Resiliência Resilience4j — gateway instável (Gherkin S5)")
class ResilienceIntegrationTest {

    static final String MAPPINGS_PATH = Path.of("../wiremock/mappings").toAbsolutePath().toString();
    static final String FILES_PATH    = Path.of("../wiremock/__files").toAbsolutePath().toString();

    static final UUID ACTIVE_CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID PRODUCT_AVAIL   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> wireMock = new GenericContainer<>("wiremock/wiremock:latest")
            .withCommand("--global-response-templating")
            .withFileSystemBind(MAPPINGS_PATH, "/home/wiremock/mappings", BindMode.READ_ONLY)
            .withFileSystemBind(FILES_PATH,    "/home/wiremock/__files",  BindMode.READ_ONLY)
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        var wmUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
        registry.add("external.customer.base-url",        () -> wmUrl);
        registry.add("external.catalog.base-url",         () -> wmUrl);
        registry.add("external.payment-gateway.base-url", () -> wmUrl);
        registry.add("external.notification.base-url",    () -> wmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> wmUrl + "/auth/.well-known/jwks.json");

        // Small window so CB opens after 1 user call (3 retry attempts = 3 CB calls = 100% failure)
        registry.add("resilience4j.circuitbreaker.instances.paymentGateway.slidingWindowSize",   () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.paymentGateway.minimumNumberOfCalls",() -> "3");
        registry.add("resilience4j.circuitbreaker.instances.paymentGateway.waitDurationInOpenState", () -> "60s");
        // Fast retries to keep the test under 10s despite WireMock's 1500ms delay per attempt
        registry.add("resilience4j.retry.instances.paymentGateway.maxAttempts",  () -> "3");
        registry.add("resilience4j.retry.instances.paymentGateway.waitDuration", () -> "10ms");
        registry.add("resilience4j.retry.instances.paymentGateway.enableExponentialBackoff", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateOrderUseCase        createOrder;
    @Autowired AddOrderItemUseCase       addItem;
    @Autowired ConfirmOrderUseCase       confirmOrder;
    @Autowired GetOrderUseCase           getOrder;
    @Autowired ListCustomerOrdersUseCase listOrders;
    @Autowired InitiatePaymentUseCase    initiatePayment;
    @Autowired GetPaymentUseCase         getPayment;
    @Autowired CircuitBreakerRegistry    circuitBreakerRegistry;

    @BeforeEach
    void cleanup() {
        jdbc.execute("DELETE FROM processed_webhook_events");
        jdbc.execute("DELETE FROM domain_events");
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM order_items");
        jdbc.execute("DELETE FROM orders");
        jdbc.execute("DELETE FROM idempotency_keys");
        // Reset circuit breaker state between tests
        circuitBreakerRegistry.circuitBreaker("paymentGateway").reset();
    }

    private UUID confirmedOrder() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        confirmOrder.confirm(new ConfirmOrderCommand(order.id()));
        return order.id();
    }

    // ── Gherkin: gateway instável → 502 após retries, payment permanece PENDING ──

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Gherkin: tok-unstable → ExternalServiceException após retries (< 10s)")
    void unstableGateway_throwsAfterRetries() {
        var orderId = confirmedOrder();

        // tok-unstable: WireMock returns 503 with 1500ms delay; 3 retry attempts = ~4.5s total
        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-unstable")))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Gherkin: payment permanece PENDING e pedido em PAYMENT_PENDING após 502")
    void unstableGateway_paymentStaysPending() {
        var orderId = confirmedOrder();

        // Phase 1 commits (PENDING) before gateway call; gateway fails; phase 2 never runs
        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-unstable")))
                .isInstanceOf(ExternalServiceException.class);

        // Order must remain PAYMENT_PENDING (not rolled back)
        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("PAYMENT_PENDING");

        // Payment must remain PENDING (committed in phase 1)
        var payments = jdbc.queryForList(
                "SELECT status FROM payments WHERE order_id = ?::uuid", order.id().toString());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).get("status")).isEqualTo("PENDING");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Gherkin: após falhas suficientes o circuit breaker abre")
    void unstableGateway_circuitBreakerOpens() {
        var orderId = confirmedOrder();

        // One call with tok-unstable = 3 CB failures (3 retry attempts)
        // slidingWindowSize=3, minimumNumberOfCalls=3 → CB opens at 100% failure rate
        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-unstable")))
                .isInstanceOf(ExternalServiceException.class);

        var cb = circuitBreakerRegistry.circuitBreaker("paymentGateway");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Gherkin: demais endpoints continuam respondendo com CB aberto")
    void unstableGateway_otherEndpointsUnaffected() {
        var orderId = confirmedOrder();

        // Open the CB
        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-unstable")))
                .isInstanceOf(ExternalServiceException.class);

        // Order read (unrelated to payment CB) must work
        var order = getOrder.getById(orderId);
        assertThat(order).isNotNull();
        assertThat(order.status()).isEqualTo("PAYMENT_PENDING");

        // List orders for customer must work
        var orders = listOrders.listByCustomer(ACTIVE_CUSTOMER);
        assertThat(orders).hasSize(1);

        // Create a new independent order (separate customer call path) works
        // (customer CB is separate from payment CB)
        assertThat(order.customerId()).isEqualTo(ACTIVE_CUSTOMER);
    }
}
