package com.commerceops.inventory.service.exception;

public class InsufficientStockException extends RuntimeException {

    private final String sku;

    public InsufficientStockException(String sku, String message) {
        super(message);
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
