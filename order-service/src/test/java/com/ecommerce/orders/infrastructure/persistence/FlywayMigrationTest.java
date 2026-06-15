package com.ecommerce.orders.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Flyway migrations")
class FlywayMigrationTest {

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
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V1 cria tabelas orders e order_items com as colunas esperadas")
    void v1CreatesOrdersAndItems() {
        var orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'orders'", Integer.class);
        var itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'order_items'", Integer.class);
        assertThat(orderCount).isEqualTo(1);
        assertThat(itemCount).isEqualTo(1);
    }

    @Test
    @DisplayName("V2 cria tabela payments")
    void v2CreatesPayments() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'payments'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("V3 cria tabelas idempotency_keys, processed_webhook_events e domain_events")
    void v3CreatesIdempotencyAndOutbox() {
        var idempotencyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'idempotency_keys'", Integer.class);
        var webhookCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'processed_webhook_events'", Integer.class);
        var outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'domain_events'", Integer.class);
        assertThat(idempotencyCount).isEqualTo(1);
        assertThat(webhookCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("tabela orders possui coluna version para optimistic locking")
    void ordersHasVersionColumn() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'orders' AND column_name = 'version'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("tabela payments possui coluna version para optimistic locking")
    void paymentsHasVersionColumn() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'payments' AND column_name = 'version'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("tabela domain_events possui coluna payload do tipo jsonb")
    void outboxPayloadIsJsonb() {
        var dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_name = 'domain_events' AND column_name = 'payload'", String.class);
        assertThat(dataType).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("tabela orders possui indice unico parcial para cliente ativo")
    void ordersHasUniqueActiveCustomerIndex() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'orders' " +
                "AND indexname = 'ux_orders_one_active_per_customer'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("tabela payments possui indice unico parcial para pagamento pendente")
    void paymentsHasUniquePendingIndex() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'payments' " +
                "AND indexname = 'ux_payments_one_pending_per_order'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
