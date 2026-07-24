package com.commerceops.shipping.carrier;

import com.commerceops.common.events.Payloads;

import java.math.BigDecimal;
import java.util.List;

public interface ShippingProvider {

    BookingResult book(BookingRequest request);

    record BookingRequest(
            String orderId,
            String customerId,
            List<Payloads.OrderLine> lines,
            Payloads.ShippingAddress address,
            BigDecimal weightKg
    ) {}

    record BookingResult(
            boolean success,
            String carrier,
            String carrierOrderId,
            String trackingNumber,
            String labelUrl,
            String failureReason
    ) {
        public static BookingResult ok(
                String carrier, String carrierOrderId, String trackingNumber, String labelUrl) {
            return new BookingResult(true, carrier, carrierOrderId, trackingNumber, labelUrl, null);
        }

        public static BookingResult failed(String carrier, String reason) {
            return new BookingResult(false, carrier, null, null, null, reason);
        }
    }
}
