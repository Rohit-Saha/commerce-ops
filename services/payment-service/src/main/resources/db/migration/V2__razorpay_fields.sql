-- Razorpay authorize/capture fields
ALTER TABLE payments
    ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'simulated',
    ADD COLUMN provider_order_id VARCHAR(128),
    ADD COLUMN provider_payment_id VARCHAR(128);

-- Allow authorize-before-saga: idempotency_key may be provisional for AUTHORIZED rows
ALTER TABLE payments ALTER COLUMN idempotency_key DROP NOT NULL;

CREATE UNIQUE INDEX uq_payments_provider_order_id
    ON payments (provider_order_id)
    WHERE provider_order_id IS NOT NULL;

CREATE INDEX idx_payments_provider_payment_id ON payments (provider_payment_id);
