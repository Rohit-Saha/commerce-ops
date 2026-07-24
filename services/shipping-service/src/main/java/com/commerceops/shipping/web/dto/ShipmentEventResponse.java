package com.commerceops.shipping.web.dto;

import com.commerceops.shipping.domain.ShipmentEvent;
import com.commerceops.shipping.domain.ShipmentStatus;

import java.time.Instant;

public record ShipmentEventResponse(
        Long id,
        ShipmentStatus status,
        String rawCode,
        String message,
        Instant occurredAt
) {
    public static ShipmentEventResponse from(ShipmentEvent event) {
        return new ShipmentEventResponse(
                event.getId(),
                event.getStatus(),
                event.getRawCode(),
                event.getMessage(),
                event.getOccurredAt());
    }
}
