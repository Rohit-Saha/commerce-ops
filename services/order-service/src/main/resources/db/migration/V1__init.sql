CREATE TABLE orders (
    id              VARCHAR(36)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    total_amount    NUMERIC(19, 2)  NOT NULL,
    currency        VARCHAR(8)      NOT NULL,
    idempotency_key VARCHAR(128),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_orders_idempotency_key ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_lines (
    id          BIGSERIAL       NOT NULL,
    order_id    VARCHAR(36)     NOT NULL,
    sku         VARCHAR(64)     NOT NULL,
    quantity    INTEGER         NOT NULL,
    unit_price  NUMERIC(19, 2)  NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_order_lines_order_id ON order_lines (order_id);

CREATE TABLE client_idempotency (
    idempotency_key VARCHAR(128) NOT NULL,
    order_id        VARCHAR(36)  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_client_idempotency PRIMARY KEY (idempotency_key)
);

CREATE TABLE outbox_events (
    id            BIGSERIAL      NOT NULL,
    topic         VARCHAR(128)   NOT NULL,
    aggregate_id  VARCHAR(128)   NOT NULL,
    event_type    VARCHAR(128)   NOT NULL,
    payload       TEXT           NOT NULL,
    status        VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    published_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_status ON outbox_events (status);

CREATE TABLE processed_events (
    consumer_group VARCHAR(128) NOT NULL,
    event_id       VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer_group, event_id)
);
