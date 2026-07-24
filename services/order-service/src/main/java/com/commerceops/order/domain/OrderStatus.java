package com.commerceops.order.domain;

public enum OrderStatus {
    PENDING,
    RESERVING,
    RESERVED,
    PAYMENT_PENDING,
    PAID,
    SHIPPING,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this == DELIVERED || this == COMPLETED || this == CANCELLED || this == FAILED;
    }

    /** Statuses where customer/admin may request cancel before a shipment is booked. */
    public boolean isCancellablePreShip() {
        return this == PENDING
                || this == RESERVING
                || this == RESERVED
                || this == PAYMENT_PENDING
                || this == PAID;
    }
}
