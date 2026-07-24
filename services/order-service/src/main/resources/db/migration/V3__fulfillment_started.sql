-- Deferred fulfillment: OrderCreated published only after payment authorize
ALTER TABLE orders
    ADD COLUMN fulfillment_started BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing orders already had OrderCreated emitted
UPDATE orders SET fulfillment_started = TRUE;
