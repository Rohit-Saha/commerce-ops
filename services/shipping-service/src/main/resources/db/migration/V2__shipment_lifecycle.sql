CREATE TABLE shipment_events (
    id           BIGSERIAL PRIMARY KEY,
    shipment_id  BIGINT       NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    status       VARCHAR(32)  NOT NULL,
    raw_code     VARCHAR(64),
    message      VARCHAR(512),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_shipment_events_shipment_id ON shipment_events (shipment_id);

ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS carrier            VARCHAR(64),
    ADD COLUMN IF NOT EXISTS carrier_order_id   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS label_url          VARCHAR(512),
    ADD COLUMN IF NOT EXISTS status_reason      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS status_updated_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS recipient_name     VARCHAR(128),
    ADD COLUMN IF NOT EXISTS address_line1      VARCHAR(256),
    ADD COLUMN IF NOT EXISTS address_line2      VARCHAR(256),
    ADD COLUMN IF NOT EXISTS city               VARCHAR(128),
    ADD COLUMN IF NOT EXISTS state              VARCHAR(64),
    ADD COLUMN IF NOT EXISTS postal_code        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS country            VARCHAR(64),
    ADD COLUMN IF NOT EXISTS weight_kg          NUMERIC(10, 3);

UPDATE shipments SET status_updated_at = created_at WHERE status_updated_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_shipments_active_order
    ON shipments (order_id)
    WHERE status <> 'FAILED' AND status <> 'CANCELLED';
