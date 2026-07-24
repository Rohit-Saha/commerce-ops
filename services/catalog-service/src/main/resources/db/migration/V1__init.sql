CREATE TABLE catalog_products (
    sku            VARCHAR(64)     NOT NULL,
    name           VARCHAR(128)    NOT NULL,
    unit_price     NUMERIC(12, 2)  NOT NULL,
    available_qty  INTEGER         NOT NULL DEFAULT 0,
    deleted        BOOLEAN         NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_catalog_products PRIMARY KEY (sku),
    CONSTRAINT chk_catalog_products_available_nonneg CHECK (available_qty >= 0),
    CONSTRAINT chk_catalog_products_unit_price_nonneg CHECK (unit_price >= 0)
);

CREATE INDEX idx_catalog_products_active ON catalog_products (sku) WHERE deleted = FALSE;

CREATE TABLE processed_events (
    consumer_group VARCHAR(128) NOT NULL,
    event_id       VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer_group, event_id)
);
