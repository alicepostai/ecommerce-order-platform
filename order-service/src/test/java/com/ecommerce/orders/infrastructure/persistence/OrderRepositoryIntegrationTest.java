package com.ecommerce.orders.infrastructure.persistence;

import com.ecommerce.orders.application.port.out.OrderRepository;
import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@DisplayName("OrderRepository — integração")
class OrderRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/does-not-matter");
    }

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    JpaOrderRepository jpaOrderRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final CustomerId CUSTOMER_ID = new CustomerId(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));

    // ── save & findById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("save persiste e findById recupera o pedido")
    void saveAndFindById() {
        var order = newOrder();

        var saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        var found = orderRepository.findById(order.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(order.getId());
        assertThat(found.get().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("findById retorna vazio quando pedido nao existe")
    void findByIdReturnsEmptyWhenNotFound() {
        var result = orderRepository.findById(OrderId.generate());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save atualiza pedido existente")
    void saveUpdatesExistingOrder() {
        var order = newOrder();
        orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        order.addItem(OrderItemId.generate(), new ProductId(UUID.randomUUID()), new Quantity(3));
        orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        var found = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getQuantity().value()).isEqualTo(3);
    }

    // ── findByCustomerId ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCustomerId retorna todos os pedidos do cliente")
    void findByCustomerId() {
        // Cancel first order so we can create a second (unique partial index allows this)
        var order1 = newOrder();
        order1.cancel(CancellationReason.CUSTOMER_REQUEST);
        orderRepository.save(order1);
        entityManager.flush();

        var order2 = Order.create(OrderId.generate(), CUSTOMER_ID);
        orderRepository.save(order2);
        entityManager.flush();
        entityManager.clear();

        var orders = orderRepository.findByCustomerId(CUSTOMER_ID);

        assertThat(orders).hasSize(2)
                .extracting(Order::getId)
                .containsExactlyInAnyOrder(order1.getId(), order2.getId());
    }

    @Test
    @DisplayName("findByCustomerId retorna lista vazia quando cliente sem pedidos")
    void findByCustomerIdReturnsEmptyList() {
        var result = orderRepository.findByCustomerId(new CustomerId(UUID.randomUUID()));
        assertThat(result).isEmpty();
    }

    // ── hasActiveOrderForCustomer ─────────────────────────────────────────────

    @Test
    @DisplayName("hasActiveOrderForCustomer retorna true para pedido CREATED")
    void hasActiveOrderForCustomerReturnsTrueForCreated() {
        orderRepository.save(newOrder());
        entityManager.flush();

        assertThat(orderRepository.hasActiveOrderForCustomer(CUSTOMER_ID)).isTrue();
    }

    @Test
    @DisplayName("hasActiveOrderForCustomer retorna false quando sem pedido ativo")
    void hasActiveOrderForCustomerReturnsFalseWhenNone() {
        assertThat(orderRepository.hasActiveOrderForCustomer(CUSTOMER_ID)).isFalse();
    }

    @Test
    @DisplayName("hasActiveOrderForCustomer retorna false para pedido CANCELLED")
    void hasActiveOrderForCustomerReturnsFalseForCancelled() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        orderRepository.save(order);
        entityManager.flush();

        assertThat(orderRepository.hasActiveOrderForCustomer(CUSTOMER_ID)).isFalse();
    }

    // ── unique partial index ──────────────────────────────────────────────────

    @Test
    @DisplayName("salvar dois pedidos ativos do mesmo cliente viola indice unico parcial")
    void twoActiveOrdersForSameCustomerViolatesUniqueIndex() {
        var order1 = newOrder();
        var order2 = Order.create(OrderId.generate(), CUSTOMER_ID);

        orderRepository.save(order1);
        entityManager.flush();

        // jpaOrderRepository.flush() goes through Spring's exception translation
        assertThatThrownBy(() -> {
            orderRepository.save(order2);
            jpaOrderRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cliente pode ter novo pedido apos cancelar o anterior")
    void newOrderAllowedAfterCancellation() {
        var order1 = newOrder();
        order1.cancel(CancellationReason.CUSTOMER_REQUEST);
        orderRepository.save(order1);
        entityManager.flush();

        var order2 = Order.create(OrderId.generate(), CUSTOMER_ID);
        orderRepository.save(order2);
        entityManager.flush();

        var orders = orderRepository.findByCustomerId(CUSTOMER_ID);
        assertThat(orders).hasSize(2);
    }

    // ── optimistic locking ────────────────────────────────────────────────────

    @Test
    @DisplayName("modificacao concorrente sobre a mesma versao lanca ObjectOptimisticLockingFailureException")
    void concurrentModificationThrowsOptimisticLockException() {
        var order = newOrder();
        orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        // Load managed entity (version=0 tracked by EM)
        var entity = jpaOrderRepository.findById(order.getId().value()).orElseThrow();

        // Simulate concurrent update by incrementing version directly in DB
        jdbcTemplate.update(
                "UPDATE orders SET status = 'CONFIRMED', version = version + 1 WHERE id = ?",
                order.getId().value());

        // Dirty the managed entity — EM still thinks version=0
        entity.setStatus("PAYMENT_PENDING");

        // flush() via Spring Data gets exception translation: StaleObjectStateException → ObjectOptimisticLockingFailureException
        assertThatThrownBy(() -> jpaOrderRepository.flush())
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Order newOrder() {
        return Order.create(OrderId.generate(), CUSTOMER_ID);
    }

}
