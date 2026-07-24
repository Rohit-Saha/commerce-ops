package com.commerceops.shipping.web.dto;

import com.commerceops.shipping.domain.Shipment;
import com.commerceops.shipping.domain.ShipmentEvent;
import com.commerceops.shipping.domain.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShipmentResponse(
        Long id,
        String orderId,
        String sagaId,
        String trackingNumber,
        String carrier,
        String carrierOrderId,
        String labelUrl,
        ShipmentStatus status,
        String failureReason,
        String statusReason,
        Instant statusUpdatedAt,
        String recipientName,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        BigDecimal weightKg,
        Instant createdAt,
        List<ShipmentEventResponse> events
) {
    public static ShipmentResponse from(Shipment shipment) {
        return from(shipment, List.of());
    }

    public static ShipmentResponse from(Shipment shipment, List<ShipmentEvent> events) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getSagaId(),
                shipment.getTrackingNumber(),
                shipment.getCarrier(),
                shipment.getCarrierOrderId(),
                shipment.getLabelUrl(),
                shipment.getStatus(),
                shipment.getFailureReason(),
                shipment.getStatusReason(),
                shipment.getStatusUpdatedAt(),
                shipment.getRecipientName(),
                shipment.getAddressLine1(),
                shipment.getAddressLine2(),
                shipment.getCity(),
                shipment.getState(),
                shipment.getPostalCode(),
                shipment.getCountry(),
                shipment.getWeightKg(),
                shipment.getCreatedAt(),
                events.stream().map(ShipmentEventResponse::from).toList());
    }
}
