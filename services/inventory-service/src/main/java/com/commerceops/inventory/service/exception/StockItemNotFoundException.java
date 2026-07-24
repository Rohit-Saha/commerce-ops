package com.commerceops.inventory.service.exception;

public class StockItemNotFoundException extends RuntimeException {

    public StockItemNotFoundException(String sku) {
        super("Stock item not found: " + sku);
    }
}
