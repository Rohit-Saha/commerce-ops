package com.commerceops.shipping.domain;

public enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    RTO,
    CANCELLED,
    FAILED
}
