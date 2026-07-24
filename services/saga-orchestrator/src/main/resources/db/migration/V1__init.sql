CREATE TABLE saga_instances (
    id              BIGSERIAL PRIMARY KEY,
    order_id        VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    current_step    VARCHAR(64),
    reservation_id  VARCHAR(64),
    payment_id      VARCHAR(64),
    shipment_id     VARCHAR(64),
    customer_id     VARCHAR(64),
    payload_json    TEXT,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    step_deadline   TIMESTAMP,
    last_error      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_saga_instances_order_id UNIQUE (order_id)
);

CREATE INDEX idx_saga_instances_status_deadline ON saga_instances (status, step_deadline);

CREATE TABLE saga_steps (
    id          BIGSERIAL PRIMARY KEY,
    saga_id     BIGINT NOT NULL REFERENCES saga_instances (id),
    step_name   VARCHAR(64) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    detail      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_saga_steps_saga_id ON saga_steps (saga_id);

CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    topic         VARCHAR(128) NOT NULL,
    aggregate_id  VARCHAR(64) NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    payload       TEXT NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    published_at  TIMESTAMP
);

CREATE INDEX idx_outbox_events_status ON outbox_events (status);

CREATE TABLE processed_events (
    consumer_group  VARCHAR(64) NOT NULL,
    event_id        VARCHAR(64) NOT NULL,
    processed_at    TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);
