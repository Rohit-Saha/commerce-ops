package com.commerceops.inventory.service.exception;

public class StockItemInUseException extends RuntimeException {

    public StockItemInUseException(String sku, int reservedQty) {
        super("Cannot soft-delete stock item " + sku + ": reserved_qty=" + reservedQty);
    }
}
