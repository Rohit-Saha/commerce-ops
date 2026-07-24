CREATE TABLE invoices (
    id                BIGSERIAL PRIMARY KEY,
    invoice_number    VARCHAR(32)     NOT NULL,
    order_id          VARCHAR(64)     NOT NULL,
    shipment_id       VARCHAR(64),
    customer_id       VARCHAR(64)     NOT NULL,
    currency          VARCHAR(8)      NOT NULL DEFAULT 'INR',
    subtotal          NUMERIC(19, 2)  NOT NULL,
    cgst              NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    sgst              NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    igst              NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    total             NUMERIC(19, 2)  NOT NULL,
    status            VARCHAR(32)     NOT NULL,
    buyer_name        VARCHAR(128),
    buyer_line1       VARCHAR(256),
    buyer_line2       VARCHAR(256),
    buyer_city        VARCHAR(128),
    buyer_state       VARCHAR(64),
    buyer_postal_code VARCHAR(32),
    buyer_country     VARCHAR(64),
    seller_legal_name VARCHAR(256)    NOT NULL,
    seller_gstin      VARCHAR(32)     NOT NULL,
    seller_address    TEXT            NOT NULL,
    seller_state      VARCHAR(64)     NOT NULL,
    seller_state_code VARCHAR(8)      NOT NULL,
    payment_ref       VARCHAR(128),
    pdf_bytes         BYTEA,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uk_invoices_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoices_order_id UNIQUE (order_id)
);

CREATE INDEX idx_invoices_customer_id ON invoices (customer_id);
CREATE INDEX idx_invoices_created_at ON invoices (created_at);

CREATE TABLE invoice_lines (
    id           BIGSERIAL PRIMARY KEY,
    invoice_id   BIGINT         NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    line_no      INT            NOT NULL,
    sku          VARCHAR(64)    NOT NULL,
    description  VARCHAR(256)   NOT NULL,
    quantity     INT            NOT NULL,
    unit_price   NUMERIC(19, 2) NOT NULL,
    line_gross   NUMERIC(19, 2) NOT NULL,
    taxable      NUMERIC(19, 2) NOT NULL,
    cgst         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    sgst         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    igst         NUMERIC(19, 2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines (invoice_id);

CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;

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
