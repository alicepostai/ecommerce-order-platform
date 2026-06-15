CREATE TABLE orders (
    id               UUID        NOT NULL,
    customer_id      UUID        NOT NULL,
    status           VARCHAR(20) NOT NULL,
    total_amount     NUMERIC(19,2),
    total_currency   VARCHAR(3),
    payment_attempts INT         NOT NULL DEFAULT 0,
    cancellation_reason VARCHAR(40),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE order_items (
    id             UUID         NOT NULL,
    order_id       UUID         NOT NULL,
    product_id     UUID         NOT NULL,
    product_name   VARCHAR(255),
    quantity       INT          NOT NULL,
    unit_price_amount   NUMERIC(19,2),
    unit_price_currency VARCHAR(3),
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
