package com.commerceops.customer.service.exception;

public class AddressNotFoundException extends RuntimeException {
    public AddressNotFoundException(String id) {
        super("Address not found: " + id);
    }
}
