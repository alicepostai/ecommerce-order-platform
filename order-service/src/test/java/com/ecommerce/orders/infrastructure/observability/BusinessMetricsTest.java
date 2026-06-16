package com.ecommerce.orders.infrastructure.observability;

import com.ecommerce.orders.application.port.out.MetricsPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessMetrics — contadores de negocio (T18)")
class BusinessMetricsTest {

    @Test
    @DisplayName("Tres contadores sao registrados no MeterRegistry ao instanciar")
    void all_three_counters_are_registered_on_creation() {
        var registry = new SimpleMeterRegistry();
        new BusinessMetrics(registry);

        assertThat(registry.find("orders_confirmed_total").counter()).isNotNull();
        assertThat(registry.find("payments_rejected_total").counter()).isNotNull();
        assertThat(registry.find("orders_auto_cancelled_total").counter()).isNotNull();
    }

    @Test
    @DisplayName("incrementOrdersConfirmed incrementa o contador correto")
    void increment_orders_confirmed() {
        var registry = new SimpleMeterRegistry();
        MetricsPort metrics = new BusinessMetrics(registry);

        metrics.incrementOrdersConfirmed();
        metrics.incrementOrdersConfirmed();

        assertThat(registry.counter("orders_confirmed_total").count()).isEqualTo(2.0);
        assertThat(registry.counter("payments_rejected_total").count()).isEqualTo(0.0);
        assertThat(registry.counter("orders_auto_cancelled_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("incrementPaymentsRejected incrementa o contador correto")
    void increment_payments_rejected() {
        var registry = new SimpleMeterRegistry();
        MetricsPort metrics = new BusinessMetrics(registry);

        metrics.incrementPaymentsRejected();

        assertThat(registry.counter("payments_rejected_total").count()).isEqualTo(1.0);
        assertThat(registry.counter("orders_confirmed_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("incrementOrdersAutoCancelled incrementa o contador correto")
    void increment_orders_auto_cancelled() {
        var registry = new SimpleMeterRegistry();
        MetricsPort metrics = new BusinessMetrics(registry);

        metrics.incrementOrdersAutoCancelled();

        assertThat(registry.counter("orders_auto_cancelled_total").count()).isEqualTo(1.0);
        assertThat(registry.counter("orders_confirmed_total").count()).isEqualTo(0.0);
    }
}
