package com.ecommerce.orders.application.port.out;

public interface MetricsPort {
    void incrementOrdersConfirmed();
    void incrementPaymentsRejected();
    void incrementOrdersAutoCancelled();
}
