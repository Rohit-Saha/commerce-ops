CREATE TABLE customers (
    id              VARCHAR(36)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(128)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_customers_email ON customers (LOWER(email));

CREATE TABLE customer_addresses (
    id              VARCHAR(36)     NOT NULL,
    customer_id     VARCHAR(36)     NOT NULL,
    recipient_name  VARCHAR(128)    NOT NULL,
    line1           VARCHAR(256)    NOT NULL,
    line2           VARCHAR(256),
    city            VARCHAR(128)    NOT NULL,
    state           VARCHAR(64)     NOT NULL,
    postal_code     VARCHAR(32)     NOT NULL,
    country         VARCHAR(64)     NOT NULL DEFAULT 'US',
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_customer_addresses PRIMARY KEY (id),
    CONSTRAINT fk_customer_addresses_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE CASCADE
);

CREATE INDEX idx_customer_addresses_customer_id ON customer_addresses (customer_id);
CREATE UNIQUE INDEX uq_customer_addresses_one_default
    ON customer_addresses (customer_id)
    WHERE is_default = TRUE;
