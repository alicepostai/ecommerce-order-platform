CREATE TABLE payments (
    id             UUID           NOT NULL,
    order_id       UUID           NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    attempt_number INT            NOT NULL,
    transaction_id VARCHAR(64),
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE UNIQUE INDEX ux_payments_one_pending_per_order ON payments(order_id) WHERE status = 'PENDING';
CREATE INDEX idx_payments_order_id ON payments(order_id);
