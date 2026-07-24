ALTER TABLE stock_items
    ADD COLUMN unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE stock_items SET unit_price = 29.00 WHERE sku = 'SKU-TEE-001';
UPDATE stock_items SET unit_price = 12.00 WHERE sku = 'SKU-MUG-001';
UPDATE stock_items SET unit_price = 15.00 WHERE sku = 'SKU-HAT-001';

ALTER TABLE stock_items
    ADD CONSTRAINT chk_stock_items_unit_price_nonneg CHECK (unit_price >= 0);
