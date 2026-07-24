CREATE TABLE stock_items (
    sku            VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    available_qty  INTEGER      NOT NULL DEFAULT 0,
    reserved_qty   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_items PRIMARY KEY (sku),
    CONSTRAINT chk_stock_items_available_nonneg CHECK (available_qty >= 0),
    CONSTRAINT chk_stock_items_reserved_nonneg CHECK (reserved_qty >= 0)
);

CREATE TABLE reservations (
    id          VARCHAR(36)  NOT NULL,
    order_id    VARCHAR(36)  NOT NULL,
    saga_id     VARCHAR(36),
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reservations PRIMARY KEY (id)
);

CREATE INDEX idx_reservations_order_id ON reservations (order_id);
CREATE INDEX idx_reservations_status ON reservations (status);

CREATE TABLE reservation_lines (
    id             BIGSERIAL   NOT NULL,
    reservation_id VARCHAR(36) NOT NULL,
    sku            VARCHAR(64) NOT NULL,
    quantity       INTEGER     NOT NULL,
    CONSTRAINT pk_reservation_lines PRIMARY KEY (id),
    CONSTRAINT fk_reservation_lines_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE
);

CREATE INDEX idx_reservation_lines_reservation_id ON reservation_lines (reservation_id);

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
