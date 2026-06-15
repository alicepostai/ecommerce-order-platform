CREATE TABLE payments (
    id             UUID        NOT NULL,
    order_id       UUID        NOT NULL,
    card_token     VARCHAR(255) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    attempt_number INT         NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
