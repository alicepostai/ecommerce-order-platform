package com.ecommerce.orders.infrastructure.client;

import com.ecommerce.orders.application.exception.ExternalServiceException;
import com.ecommerce.orders.application.port.out.CustomerGateway;
import com.ecommerce.orders.application.port.out.NotificationPort;
import com.ecommerce.orders.application.port.out.PaymentGatewayPort;
import com.ecommerce.orders.application.port.out.ProductCatalogGateway;
import com.ecommerce.orders.domain.exception.CustomerBlockedException;
import com.ecommerce.orders.domain.exception.CustomerNotFoundException;
import com.ecommerce.orders.domain.exception.ProductNotFoundException;
import com.ecommerce.orders.domain.exception.ProductUnavailableException;
import com.ecommerce.orders.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static com.ecommerce.orders.application.port.out.PaymentGatewayPort.ChargeStatus.APPROVED;
import static com.ecommerce.orders.application.port.out.PaymentGatewayPort.ChargeStatus.REJECTED;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Clientes HTTP — integração com WireMock")
class ExternalClientsIntegrationTest {

    static final String MAPPINGS_PATH = Path.of("../wiremock/mappings").toAbsolutePath().toString();
    static final String FILES_PATH    = Path.of("../wiremock/__files").toAbsolutePath().toString();

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
        registry.add("external.customer.base-url",       () -> wmUrl);
        registry.add("external.catalog.base-url",        () -> wmUrl);
        registry.add("external.payment-gateway.base-url",() -> wmUrl);
        registry.add("external.notification.base-url",   () -> wmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> wmUrl + "/auth/.well-known/jwks.json");
    }

    @Autowired CustomerGateway     customerGateway;
    @Autowired ProductCatalogGateway catalogGateway;
    @Autowired PaymentGatewayPort  paymentGateway;
    @Autowired NotificationPort    notificationPort;

    // ── CustomerGateway ──────────────────────────────────────────────────────

    @Test
    @DisplayName("cliente ativo valida sem lancar excecao")
    void activeCustomerValidatesOk() {
        var activeId = new CustomerId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThatCode(() -> customerGateway.validateActiveCustomer(activeId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cliente bloqueado lanca CustomerBlockedException")
    void blockedCustomerThrowsException() {
        var blockedId = new CustomerId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThatThrownBy(() -> customerGateway.validateActiveCustomer(blockedId))
                .isInstanceOf(CustomerBlockedException.class);
    }

    @Test
    @DisplayName("cliente inexistente lanca CustomerNotFoundException")
    void unknownCustomerThrowsException() {
        var unknownId = new CustomerId(UUID.randomUUID());
        assertThatThrownBy(() -> customerGateway.validateActiveCustomer(unknownId))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // ── ProductCatalogGateway ─────────────────────────────────────────────────

    @Test
    @DisplayName("produto disponivel retorna ProductSnapshot com preco correto")
    void availableProductReturnsSnapshot() {
        var productId = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        var snapshot = catalogGateway.fetchProduct(productId);

        assertThat(snapshot.productId()).isEqualTo(productId);
        assertThat(snapshot.productName()).isEqualTo("Teclado Mecânico TKL");
        assertThat(snapshot.unitPrice().amount()).isEqualByComparingTo(new BigDecimal("199.90"));
        assertThat(snapshot.unitPrice().currency()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("segundo produto disponivel retorna snapshot com preco correto")
    void secondAvailableProductReturnsSnapshot() {
        var productId = new ProductId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        var snapshot = catalogGateway.fetchProduct(productId);

        assertThat(snapshot.productName()).isEqualTo("Mouse Sem Fio");
        assertThat(snapshot.unitPrice().amount()).isEqualByComparingTo(new BigDecimal("49.90"));
    }

    @Test
    @DisplayName("produto indisponivel lanca ProductUnavailableException")
    void unavailableProductThrowsException() {
        var productId = new ProductId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        assertThatThrownBy(() -> catalogGateway.fetchProduct(productId))
                .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    @DisplayName("produto inexistente lanca ProductNotFoundException")
    void unknownProductThrowsException() {
        var productId = new ProductId(UUID.randomUUID());
        assertThatThrownBy(() -> catalogGateway.fetchProduct(productId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ── PaymentGatewayPort ────────────────────────────────────────────────────

    @Test
    @DisplayName("tok-approved retorna ChargeResult APPROVED com transactionId")
    void approvedPaymentReturnsApprovedResult() {
        var result = paymentGateway.charge(
                PaymentId.generate(),
                OrderId.generate(),
                new Money(new BigDecimal("399.80"), "BRL"),
                "tok-approved");

        assertThat(result.status()).isEqualTo(APPROVED);
        assertThat(result.transactionId()).isEqualTo("tx-0001");
    }

    @Test
    @DisplayName("tok-rejected retorna ChargeResult REJECTED com transactionId")
    void rejectedPaymentReturnsRejectedResult() {
        var result = paymentGateway.charge(
                PaymentId.generate(),
                OrderId.generate(),
                new Money(new BigDecimal("399.80"), "BRL"),
                "tok-rejected");

        assertThat(result.status()).isEqualTo(REJECTED);
        assertThat(result.transactionId()).isEqualTo("tx-0002");
    }

    @Test
    @DisplayName("tok-unstable (503) lanca ExternalServiceException")
    void unstableGatewayThrowsExternalServiceException() {
        assertThatThrownBy(() -> paymentGateway.charge(
                PaymentId.generate(),
                OrderId.generate(),
                new Money(new BigDecimal("399.80"), "BRL"),
                "tok-unstable"))
                .isInstanceOf(ExternalServiceException.class);
    }

    // ── NotificationPort ──────────────────────────────────────────────────────

    @Test
    @DisplayName("envio de notificacao retorna sem lancar excecao")
    void notificationSentWithoutException() {
        var customerId = new CustomerId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThatCode(() -> notificationPort.send(
                customerId,
                NotificationPort.NotificationTemplate.ORDER_PAID,
                Map.of("orderId", UUID.randomUUID().toString(), "total", "399.80 BRL")))
                .doesNotThrowAnyException();
    }
}
