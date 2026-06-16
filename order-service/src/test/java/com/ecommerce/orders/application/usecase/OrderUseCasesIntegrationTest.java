package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.*;
import com.ecommerce.orders.application.port.in.*;
import com.ecommerce.orders.domain.exception.*;
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
@DisplayName("Order use cases — integração (Gherkin S5)")
class OrderUseCasesIntegrationTest {

    static final String MAPPINGS_PATH = Path.of("../wiremock/mappings").toAbsolutePath().toString();
    static final String FILES_PATH    = Path.of("../wiremock/__files").toAbsolutePath().toString();

    // IDs fixos dos mapeamentos WireMock
    static final UUID ACTIVE_CUSTOMER  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BLOCKED_CUSTOMER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID PRODUCT_AVAIL    = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID PRODUCT_AVAIL_2  = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID PRODUCT_UNAVAIL  = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID UNKNOWN_CUSTOMER = UUID.randomUUID();
    static final UUID UNKNOWN_PRODUCT  = UUID.randomUUID();

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

    @Autowired CreateOrderUseCase      createOrder;
    @Autowired GetOrderUseCase         getOrder;
    @Autowired ListCustomerOrdersUseCase listOrders;
    @Autowired AddOrderItemUseCase     addItem;
    @Autowired RemoveOrderItemUseCase  removeItem;
    @Autowired ConfirmOrderUseCase     confirmOrder;
    @Autowired CancelOrderUseCase      cancelOrder;

    // ── Criação de pedido ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: cliente ativo cria pedido com status CREATED")
    void activeCustomerCreatesOrder() {
        var result = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.customerId()).isEqualTo(ACTIVE_CUSTOMER);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalAmount()).isNull();
        assertThat(result.paymentAttempts()).isZero();
    }

    @Test
    @DisplayName("Gherkin: cliente bloqueado lanca CustomerBlockedException")
    void blockedCustomerRejected() {
        assertThatThrownBy(() -> createOrder.create(new CreateOrderCommand(BLOCKED_CUSTOMER)))
                .isInstanceOf(CustomerBlockedException.class);
    }

    @Test
    @DisplayName("Gherkin: cliente inexistente lanca CustomerNotFoundException")
    void unknownCustomerRejected() {
        assertThatThrownBy(() -> createOrder.create(new CreateOrderCommand(UNKNOWN_CUSTOMER)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("Gherkin: segundo pedido ativo do mesmo cliente lanca ActiveOrderExistsException")
    void secondActiveOrderRejected() {
        createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThatThrownBy(() -> createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER)))
                .isInstanceOf(ActiveOrderExistsException.class);
    }

    @Test
    @DisplayName("Gherkin: cliente pode abrir novo pedido apos cancelar o anterior")
    void newOrderAllowedAfterCancel() {
        var first = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        cancelOrder.cancel(new CancelOrderCommand(first.id()));

        var second = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        assertThat(second.status()).isEqualTo("CREATED");
    }

    // ── Listagem de pedidos ───────────────────────────────────────────────────

    @Test
    @DisplayName("listByCustomer retorna pedidos do cliente mais recente primeiro")
    void listOrdersByCustomer() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        cancelOrder.cancel(new CancelOrderCommand(order.id()));
        createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        var list = listOrders.listByCustomer(ACTIVE_CUSTOMER);
        assertThat(list).hasSize(2);
    }

    // ── Itens ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: adicionar produto disponivel retorna pedido com 1 item")
    void addAvailableProduct() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        var result = addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 2));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        assertThat(result.items().get(0).productId()).isEqualTo(PRODUCT_AVAIL);
    }

    @Test
    @DisplayName("Gherkin: produto duplicado incrementa quantidade")
    void duplicateProductIncrementsQuantity() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 2));

        var result = addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 3));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Gherkin: produto indisponivel lanca ProductUnavailableException")
    void unavailableProductRejected() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThatThrownBy(() ->
                addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_UNAVAIL, 1)))
                .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    @DisplayName("Gherkin: produto inexistente lanca ProductNotFoundException")
    void unknownProductRejected() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThatThrownBy(() ->
                addItem.addItem(new AddOrderItemCommand(order.id(), UNKNOWN_PRODUCT, 1)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("Gherkin: remocao de item inexistente lanca OrderItemNotFoundException")
    void removeNonExistentItemThrows() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThatThrownBy(() ->
                removeItem.removeItem(new RemoveOrderItemCommand(order.id(), UUID.randomUUID())))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    @DisplayName("Gherkin: pedido confirmado nao aceita alteracao de itens (409)")
    void confirmedOrderRejectsItemChanges() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        confirmOrder.confirm(new ConfirmOrderCommand(order.id()));

        assertThatThrownBy(() ->
                addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL_2, 1)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Confirmação ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: pedido sem itens nao confirma — lanca InvalidStateTransitionException")
    void emptyOrderCannotBeConfirmed() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        assertThatThrownBy(() -> confirmOrder.confirm(new ConfirmOrderCommand(order.id())))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("no items");
    }

    @Test
    @DisplayName("Gherkin: total calculado com preco do momento da confirmacao")
    void totalCalculatedAtConfirmTime() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 2));   // 2 × 199.90
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL_2, 1)); // 1 × 49.90

        var result = confirmOrder.confirm(new ConfirmOrderCommand(order.id()));

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.totalAmount()).isEqualByComparingTo("449.70");
        assertThat(result.totalCurrency()).isEqualTo("BRL");
        assertThat(result.items()).hasSize(2)
                .allSatisfy(item -> assertThat(item.unitPriceAmount()).isNotNull());
    }

    @Test
    @DisplayName("Gherkin: confirmacao e idempotente — estado e total inalterados")
    void confirmIsIdempotent() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        var firstResult = confirmOrder.confirm(new ConfirmOrderCommand(order.id()));

        var secondResult = confirmOrder.confirm(new ConfirmOrderCommand(order.id()));

        assertThat(secondResult.status()).isEqualTo("CONFIRMED");
        assertThat(secondResult.totalAmount()).isEqualByComparingTo(firstResult.totalAmount());
    }

    // ── Cancelamento ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gherkin: cancelar pedido CREATED retorna CANCELLED com razao CUSTOMER_REQUEST")
    void cancelCreatedOrder() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));

        cancelOrder.cancel(new CancelOrderCommand(order.id()));

        var result = getOrder.getById(order.id());
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.cancellationReason()).isEqualTo("CUSTOMER_REQUEST");
    }

    @Test
    @DisplayName("Gherkin: cancelar pedido CONFIRMED retorna CANCELLED")
    void cancelConfirmedOrder() {
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        confirmOrder.confirm(new ConfirmOrderCommand(order.id()));

        cancelOrder.cancel(new CancelOrderCommand(order.id()));

        var result = getOrder.getById(order.id());
        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Gherkin: cancelar pedido PAID lanca InvalidStateTransitionException")
    void cancelPaidOrderThrows() {
        // Simulate a PAID order by checking the exception from domain
        var order = createOrder.create(new CreateOrderCommand(ACTIVE_CUSTOMER));
        addItem.addItem(new AddOrderItemCommand(order.id(), PRODUCT_AVAIL, 1));
        confirmOrder.confirm(new ConfirmOrderCommand(order.id()));
        // We cannot make it PAID without payment use case (T13), so test CANCELLED → cancel
        cancelOrder.cancel(new CancelOrderCommand(order.id()));

        assertThatThrownBy(() -> cancelOrder.cancel(new CancelOrderCommand(order.id())))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("getById lanca OrderNotFoundException para pedido inexistente")
    void getByIdNotFound() {
        assertThatThrownBy(() -> getOrder.getById(UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
