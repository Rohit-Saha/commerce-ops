package com.commerceops.inventory.service.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String reference) {
        super("Reservation not found: " + reference);
    }
}
