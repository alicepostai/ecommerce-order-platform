package com.ecommerce.orders.infrastructure.observability;

import com.ecommerce.orders.application.port.out.MetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics implements MetricsPort {

    private final Counter ordersConfirmed;
    private final Counter paymentsRejected;
    private final Counter ordersAutoCancelled;

    public BusinessMetrics(MeterRegistry registry) {
        this.ordersConfirmed = Counter.builder("orders_confirmed_total")
                .description("Total number of confirmed orders")
                .register(registry);
        this.paymentsRejected = Counter.builder("payments_rejected_total")
                .description("Total number of rejected payment attempts")
                .register(registry);
        this.ordersAutoCancelled = Counter.builder("orders_auto_cancelled_total")
                .description("Total number of orders auto-cancelled due to payment attempts exceeded")
                .register(registry);
    }

    @Override
    public void incrementOrdersConfirmed() {
        ordersConfirmed.increment();
    }

    @Override
    public void incrementPaymentsRejected() {
        paymentsRejected.increment();
    }

    @Override
    public void incrementOrdersAutoCancelled() {
        ordersAutoCancelled.increment();
    }
}
