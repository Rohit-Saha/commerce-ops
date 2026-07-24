CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        VARCHAR(64)     NOT NULL,
    saga_id         VARCHAR(64),
    amount          NUMERIC(19, 2)  NOT NULL,
    currency        VARCHAR(8)      NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    idempotency_key VARCHAR(128)    NOT NULL,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_saga_id ON payments (saga_id);

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status ON outbox_events (status);

CREATE TABLE processed_events (
    consumer_group VARCHAR(128) NOT NULL,
    event_id       VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);
