package com.commerceops.catalog.service;

public class CatalogProductNotFoundException extends RuntimeException {
    public CatalogProductNotFoundException(String sku) {
        super("Product not found: " + sku);
    }
}
