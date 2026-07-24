UPDATE stock_items SET unit_price = 1299.00 WHERE sku = 'SKU-TEE-001';
UPDATE stock_items SET unit_price = 499.00 WHERE sku = 'SKU-MUG-001';
UPDATE stock_items SET unit_price = 699.00 WHERE sku = 'SKU-HAT-001';

-- Convert any remaining low USD-like prices into rupees (×40) once.
UPDATE stock_items
SET unit_price = ROUND(unit_price * 40, 2)
WHERE unit_price > 0 AND unit_price < 200;
