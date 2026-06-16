package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.*;
import com.ecommerce.orders.application.port.in.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Webhook idempotente e auto-cancel — integração (Gherkin S5)")
class WebhookIntegrationTest {

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
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateOrderUseCase             createOrder;
    @Autowired AddOrderItemUseCase            addItem;
    @Autowired ConfirmOrderUseCase            confirmOrder;
    @Autowired CancelOrderUseCase             cancelOrder;
    @Autowired GetOrderUseCase                getOrder;
    @Autowired InitiatePaymentUseCase         initiatePayment;
    @Autowired GetPaymentUseCase              getPayment;
    @Autowired ProcessPaymentCallbackUseCase  processCallback;

    @BeforeEach
    void cleanup() {
        jdbc.execute("DELETE FROM processed_webhook_events");
        jdbc.execute("DELETE FROM domain_events");
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM order_items");
        jdbc.execute("DELETE FROM orders");
        jdbc.execute("DELETE FROM idempotency_keys");
    }

    private UUID confirmedOrder() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        confirmOrder.confirm(new ConfirmOrderCommand(order.id()));
        return order.id();
    }

    // ── Gherkin: webhook idempotente (mesmo eventId) ─────────────────────────

    @Test
    @DisplayName("Gherkin: mesmo eventId processado duas vezes — no-op na segunda vez")
    void webhook_sameEventId_isIdempotent() {
        var orderId = confirmedOrder();
        var payment = initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-approved"));
        assertThat(payment.status()).isEqualTo("APPROVED");

        String eventId = "evt-idem-" + UUID.randomUUID();
        var cmd = new ProcessPaymentCallbackCommand(payment.id(), eventId, "APPROVED", "tx-0001");

        processCallback.process(cmd);
        assertThatCode(() -> processCallback.process(cmd)).doesNotThrowAnyException();

        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("PAID");
    }

    // ── Gherkin: webhook tardio para pedido cancelado ────────────────────────

    @Test
    @DisplayName("Gherkin: webhook tardio APPROVED para pedido CANCELLED → 200, ordem permanece CANCELLED")
    void webhook_lateApproved_onCancelledOrder() {
        var orderId = confirmedOrder();
        var rejectedPayment = initiatePayment.initiatePayment(
                new InitiatePaymentCommand(orderId, "tok-rejected"));

        cancelOrder.cancel(new CancelOrderCommand(orderId));
        assertThat(getOrder.getById(orderId).status()).isEqualTo("CANCELLED");

        String eventId = "evt-late-" + UUID.randomUUID();
        var cmd = new ProcessPaymentCallbackCommand(rejectedPayment.id(), eventId, "APPROVED", "tx-late");

        assertThatCode(() -> processCallback.process(cmd)).doesNotThrowAnyException();
        assertThat(getOrder.getById(orderId).status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Gherkin: mesmo eventId tardio — segunda entrega tambem e no-op")
    void webhook_lateAndRepeated_isIdempotent() {
        var orderId = confirmedOrder();
        var rejectedPayment = initiatePayment.initiatePayment(
                new InitiatePaymentCommand(orderId, "tok-rejected"));
        cancelOrder.cancel(new CancelOrderCommand(orderId));

        String eventId = "evt-late-dup-" + UUID.randomUUID();
        var cmd = new ProcessPaymentCallbackCommand(rejectedPayment.id(), eventId, "APPROVED", "tx-late2");

        processCallback.process(cmd);
        assertThatCode(() -> processCallback.process(cmd)).doesNotThrowAnyException();
    }

    // ── Gherkin: terceira rejeição cancela automaticamente ───────────────────

    @Test
    @DisplayName("Gherkin: terceira rejeicao → order CANCELLED com PAYMENT_ATTEMPTS_EXCEEDED")
    void thirdRejection_autoCancelsOrder() {
        var orderId = confirmedOrder();

        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));
        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));
        var thirdPayment = initiatePayment.initiatePayment(
                new InitiatePaymentCommand(orderId, "tok-rejected"));

        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("CANCELLED");
        assertThat(order.cancellationReason()).isEqualTo("PAYMENT_ATTEMPTS_EXCEEDED");
        assertThat(order.paymentAttempts()).isEqualTo(3);

        var payment = getPayment.getById(thirdPayment.id());
        assertThat(payment.status()).isEqualTo("REJECTED");
        assertThat(payment.attemptNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("Gherkin: terceira rejeicao via webhook → mesma logica de auto-cancel")
    void thirdRejection_viaWebhookCallback() {
        var orderId = confirmedOrder();

        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));
        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));

        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentAttempts()).isEqualTo(2);
    }
}
