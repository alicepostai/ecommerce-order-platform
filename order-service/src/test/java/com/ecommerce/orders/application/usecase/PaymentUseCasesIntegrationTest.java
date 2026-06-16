package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.*;
import com.ecommerce.orders.application.port.in.*;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;
import com.ecommerce.orders.domain.exception.OrderNotFoundException;
import com.ecommerce.orders.domain.exception.PaymentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
@DisplayName("Payment use cases — integração (Gherkin S5)")
class PaymentUseCasesIntegrationTest {

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

    @Autowired CreateOrderUseCase     createOrder;
    @Autowired AddOrderItemUseCase    addItem;
    @Autowired ConfirmOrderUseCase    confirmOrder;
    @Autowired GetOrderUseCase        getOrder;
    @Autowired InitiatePaymentUseCase initiatePayment;
    @Autowired GetPaymentUseCase      getPayment;

    // Helper: cria um pedido CONFIRMED com 1 item (produto aaaa = R$199,90)
    private UUID confirmedOrder() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        confirmOrder.confirm(new ConfirmOrderCommand(order.id()));
        return order.id();
    }

    // ── Gherkin: pagamento aprovado ──────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: pedido CONFIRMED com tok-approved → payment APPROVED, pedido PAID")
    void approvedPayment_orderBecomePaid() {
        var orderId = confirmedOrder();

        var result = initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-approved"));

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.transactionId()).isEqualTo("tx-0001");
        assertThat(result.attemptNumber()).isEqualTo(1);
        assertThat(result.orderId()).isEqualTo(orderId);

        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Gherkin: GET /payments/{id} retorna detalhes do pagamento")
    void getPayment_returnsDetails() {
        var orderId = confirmedOrder();
        var payment = initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-approved"));

        var found = getPayment.getById(payment.id());

        assertThat(found.id()).isEqualTo(payment.id());
        assertThat(found.status()).isEqualTo("APPROVED");
        assertThat(found.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Gherkin: GET /payments/{id} para pagamento inexistente lanca PaymentNotFoundException")
    void getPayment_notFound() {
        assertThatThrownBy(() -> getPayment.getById(UUID.randomUUID()))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ── Gherkin: pagamento de pedido não confirmado ──────────────────────────

    @Test
    @DisplayName("Gherkin: pagamento de pedido CREATED lanca InvalidStateTransitionException order-not-confirmed")
    void paymentOnCreatedOrder_throws() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(order.id(), "tok-approved")))
                .isInstanceOf(InvalidStateTransitionException.class)
                .extracting("errorCode")
                .isEqualTo("order-not-confirmed");
    }

    @Test
    @DisplayName("Gherkin: pagamento de pedido inexistente lanca OrderNotFoundException")
    void paymentOnMissingOrder_throws() {
        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(UUID.randomUUID(), "tok-approved")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── Gherkin: duplo clique não duplica pagamento ──────────────────────────

    @Test
    @DisplayName("Gherkin: duplo clique → segundo POST falha pois pedido sai de CONFIRMED")
    void doubleClick_secondPaymentRejected() {
        var orderId = confirmedOrder();

        // First call: moves order to PAID
        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-approved"));

        // Second call: order is PAID, not CONFIRMED → throws
        assertThatThrownBy(() ->
                initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-approved")))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Gherkin: rejeição permite nova tentativa ─────────────────────────────

    @Test
    @DisplayName("Gherkin: rejeicao → payment REJECTED, pedido volta a CONFIRMED, paymentAttempts=1")
    void rejectedPayment_orderBackToConfirmed() {
        var orderId = confirmedOrder();

        var result = initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.attemptNumber()).isEqualTo(1);

        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gherkin: segunda rejeicao → paymentAttempts=2, pedido ainda CONFIRMED")
    void twoRejections_orderStillConfirmed() {
        var orderId = confirmedOrder();

        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));
        initiatePayment.initiatePayment(new InitiatePaymentCommand(orderId, "tok-rejected"));

        var order = getOrder.getById(orderId);
        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.paymentAttempts()).isEqualTo(2);
    }
}
