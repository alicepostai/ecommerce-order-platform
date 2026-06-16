ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
        CHECK (status IN ('CREATED', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID', 'CANCELLED')),
    ADD CONSTRAINT chk_orders_payment_attempts
        CHECK (payment_attempts BETWEEN 0 AND 3);

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));
