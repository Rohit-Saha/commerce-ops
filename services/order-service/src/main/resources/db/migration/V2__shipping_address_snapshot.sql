ALTER TABLE orders
    ADD COLUMN ship_recipient_name VARCHAR(128),
    ADD COLUMN ship_line1 VARCHAR(256),
    ADD COLUMN ship_line2 VARCHAR(256),
    ADD COLUMN ship_city VARCHAR(128),
    ADD COLUMN ship_state VARCHAR(64),
    ADD COLUMN ship_postal_code VARCHAR(32),
    ADD COLUMN ship_country VARCHAR(64),
    ADD COLUMN ship_source_address_id VARCHAR(36);
