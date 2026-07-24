package com.commerceops.inventory.service.exception;

public class StockItemConflictException extends RuntimeException {

    public StockItemConflictException(String message) {
        super(message);
    }
}
