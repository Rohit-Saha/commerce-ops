package com.commerceops.shipping.carrier;

import com.commerceops.shipping.config.ChaosSettings;
import com.commerceops.shipping.config.ShippingProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SimulatedShippingProvider implements ShippingProvider {

    private final ChaosSettings chaosSettings;
    private final ShippingProperties properties;

    public SimulatedShippingProvider(ChaosSettings chaosSettings, ShippingProperties properties) {
        this.chaosSettings = chaosSettings;
        this.properties = properties;
    }

    @Override
    public BookingResult book(BookingRequest request) {
        String failure = simulateFailure(request);
        if (failure != null) {
            return BookingResult.failed("simulated", failure);
        }
        String tracking = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        // Demo label: opens as plain text in the browser (no external carrier).
        String labelUrl = "data:text/plain;charset=utf-8,"
                + "Simulated%20shipping%20label%0ATracking%3A%20" + tracking;
        return BookingResult.ok("simulated", "sim-order-" + tracking, tracking, labelUrl);
    }

    private String simulateFailure(BookingRequest request) {
        if (request.orderId() != null && request.orderId().startsWith("NOSHIP-")) {
            return "Simulated failure: orderId marked NOSHIP-";
        }
        if (request.customerId() != null && request.customerId().startsWith("NOSHIP-")) {
            return "Simulated failure: customerId marked NOSHIP-";
        }
        double rate = Math.max(chaosSettings.getFailureRate(), properties.getFailureRate());
        if (rate > 0.0 && ThreadLocalRandom.current().nextDouble() < rate) {
            return "Simulated failure: chaos failure-rate triggered (" + rate + ")";
        }
        return null;
    }
}
