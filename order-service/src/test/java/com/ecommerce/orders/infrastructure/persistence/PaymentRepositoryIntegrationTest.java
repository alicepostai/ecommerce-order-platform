package com.ecommerce.orders.infrastructure.persistence;

import com.ecommerce.orders.application.port.out.PaymentRepository;
import com.ecommerce.orders.domain.model.*;
import com.ecommerce.orders.infrastructure.persistence.repository.JpaPaymentRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@DisplayName("PaymentRepository — integração")
class PaymentRepositoryIntegrationTest {

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
    PaymentRepository paymentRepository;

    @Autowired
    JpaPaymentRepository jpaPaymentRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final OrderId ORDER_ID = new OrderId(UUID.randomUUID());
    private static final Money AMOUNT = new Money(new BigDecimal("399.80"), "BRL");

    // ── Precondição: a tabela payments tem FK para orders ─────────────────────

    private void insertOrderRow() {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, status, payment_attempts, version) " +
                "VALUES (?, ?, 'CONFIRMED', 0, 0)",
                ORDER_ID.value(), UUID.randomUUID());
    }

    // ── save & findById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("save persiste e findById recupera o pagamento")
    void saveAndFindById() {
        insertOrderRow();
        var payment = newPayment(1);

        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        var found = paymentRepository.findById(payment.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(payment.getId());
        assertThat(found.get().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.get().getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("findById retorna vazio quando pagamento nao existe")
    void findByIdReturnsEmptyWhenNotFound() {
        var result = paymentRepository.findById(PaymentId.generate());
        assertThat(result).isEmpty();
    }

    // ── findPendingByOrderId ──────────────────────────────────────────────────

    @Test
    @DisplayName("findPendingByOrderId retorna pagamento PENDING do pedido")
    void findPendingByOrderId() {
        insertOrderRow();
        var payment = newPayment(1);
        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        var found = paymentRepository.findPendingByOrderId(ORDER_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(payment.getId());
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findPendingByOrderId retorna vazio apos aprovacao")
    void findPendingByOrderIdReturnsEmptyAfterApproval() {
        insertOrderRow();
        var payment = newPayment(1);
        payment.approve("tx-0001");
        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        var found = paymentRepository.findPendingByOrderId(ORDER_ID);
        assertThat(found).isEmpty();
    }

    // ── findByOrderId ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByOrderId retorna todos os pagamentos do pedido")
    void findByOrderId() {
        insertOrderRow();
        var p1 = newPayment(1);
        p1.reject("tx-0002");
        paymentRepository.save(p1);

        var p2 = newPayment(2);
        paymentRepository.save(p2);

        entityManager.flush();
        entityManager.clear();

        var payments = paymentRepository.findByOrderId(ORDER_ID);
        assertThat(payments).hasSize(2);
    }

    // ── unique partial index ──────────────────────────────────────────────────

    @Test
    @DisplayName("dois pagamentos PENDING para o mesmo pedido violam o indice unico")
    void twoPendingPaymentsForSameOrderViolatesUniqueIndex() {
        insertOrderRow();
        var p1 = newPayment(1);
        var p2 = newPayment(2);

        paymentRepository.save(p1);
        entityManager.flush();

        // jpaPaymentRepository.flush() goes through Spring's exception translation
        assertThatThrownBy(() -> {
            paymentRepository.save(p2);
            jpaPaymentRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── optimistic locking ────────────────────────────────────────────────────

    @Test
    @DisplayName("modificacao concorrente sobre a mesma versao lanca ObjectOptimisticLockingFailureException")
    void concurrentModificationThrowsOptimisticLockException() {
        insertOrderRow();
        var payment = newPayment(1);
        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        // Load managed entity (version=0 tracked by EM)
        var entity = jpaPaymentRepository.findById(payment.getId().value()).orElseThrow();

        // Simulate concurrent update by incrementing version directly in DB
        jdbcTemplate.update(
                "UPDATE payments SET status = 'APPROVED', version = version + 1 WHERE id = ?",
                payment.getId().value());

        // Dirty the managed entity — EM still thinks version=0
        entity.setStatus("REJECTED");

        // flush() via Spring Data gets exception translation: StaleObjectStateException → ObjectOptimisticLockingFailureException
        assertThatThrownBy(() -> jpaPaymentRepository.flush())
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── save updates existing ─────────────────────────────────────────────────

    @Test
    @DisplayName("save atualiza pagamento existente ao aprovar")
    void saveUpdatesExistingPaymentOnApproval() {
        insertOrderRow();
        var payment = newPayment(1);
        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        payment.approve("tx-0001");
        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        var found = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(found.getTransactionId()).isEqualTo("tx-0001");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Payment newPayment(int attemptNumber) {
        return Payment.create(PaymentId.generate(), ORDER_ID, AMOUNT, attemptNumber);
    }
}
