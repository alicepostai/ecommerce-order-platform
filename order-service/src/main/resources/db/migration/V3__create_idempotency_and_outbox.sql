CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) NOT NULL,
    http_method     VARCHAR(10)  NOT NULL,
    request_path    VARCHAR(500) NOT NULL,
    response_status INT          NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key)
);

CREATE TABLE outbox_events (
    id           UUID        NOT NULL,
    aggregate_id UUID        NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB       NOT NULL,
    published    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events(created_at) WHERE published = FALSE;
