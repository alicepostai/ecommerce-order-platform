CREATE TABLE idempotency_keys (
    key            VARCHAR(80)  NOT NULL,
    endpoint_scope VARCHAR(120) NOT NULL,
    request_hash   VARCHAR(64)  NOT NULL,
    response_status INT         NOT NULL,
    response_body  JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (key, endpoint_scope)
);

CREATE TABLE processed_webhook_events (
    event_id     VARCHAR(80) NOT NULL,
    payment_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_processed_webhook_events PRIMARY KEY (event_id)
);

CREATE TABLE domain_events (
    id             UUID        NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(60) NOT NULL,
    payload        JSONB       NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_domain_events PRIMARY KEY (id)
);

CREATE INDEX idx_domain_events_unpublished ON domain_events(occurred_at) WHERE published = FALSE;
