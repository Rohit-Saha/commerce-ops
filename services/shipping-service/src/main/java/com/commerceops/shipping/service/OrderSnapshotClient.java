package com.commerceops.shipping.service;

import com.commerceops.common.events.Payloads;
import com.commerceops.common.web.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class OrderSnapshotClient {

    private final RestClient orderRestClient;
    private final CircuitBreaker circuitBreaker;

    public OrderSnapshotClient(
            @Qualifier("orderRestClient") RestClient orderRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderRestClient = orderRestClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("order");
    }

    public OrderSnapshot fetch(String orderId) {
        try {
            JsonNode body = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    orderRestClient.get()
                            .uri("/api/orders/{id}", orderId)
                            .retrieve()
                            .body(JsonNode.class)).get();
            JsonNode data = unwrap(body);
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw BusinessException.notFound("We couldn’t find that order.");
            }
            String status = text(data, "status");
            String customerId = text(data, "customerId");
            List<Payloads.OrderLine> lines = new ArrayList<>();
            JsonNode linesNode = data.path("lines");
            if (linesNode.isArray()) {
                for (JsonNode line : linesNode) {
                    String sku = text(line, "sku");
                    int qty = line.path("quantity").asInt(0);
                    BigDecimal price = line.has("unitPrice") && !line.get("unitPrice").isNull()
                            ? line.get("unitPrice").decimalValue()
                            : BigDecimal.ZERO;
                    if (sku != null) {
                        lines.add(new Payloads.OrderLine(sku, qty, price));
                    }
                }
            }
            JsonNode ship = data.path("shippingAddress");
            Payloads.ShippingAddress address = null;
            if (!ship.isMissingNode() && !ship.isNull()) {
                address = new Payloads.ShippingAddress(
                        text(ship, "recipientName"),
                        text(ship, "line1"),
                        text(ship, "line2"),
                        text(ship, "city"),
                        text(ship, "state"),
                        text(ship, "postalCode"),
                        text(ship, "country"));
            }
            return new OrderSnapshot(orderId, customerId, status, lines, address);
        } catch (CallNotPermittedException ex) {
            throw BusinessException.serviceUnavailable(
                    "Order service temporarily unavailable; try again shortly.");
        } catch (ResourceAccessException ex) {
            throw BusinessException.serviceUnavailable(
                    "Order service temporarily unavailable; try again shortly.");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw BusinessException.notFound("We couldn’t find that order.");
            }
            throw BusinessException.badRequest("Could not load order for shipment booking.");
        }
    }

    public Payloads.ShippingAddress fetchAddress(String orderId) {
        try {
            return fetch(orderId).shippingAddress();
        } catch (BusinessException ex) {
            return null;
        }
    }

    private static JsonNode unwrap(JsonNode body) {
        if (body != null && body.isObject() && body.path("success").isBoolean() && body.has("data")) {
            return body.get("data");
        }
        return body;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    public record OrderSnapshot(
            String orderId,
            String customerId,
            String status,
            List<Payloads.OrderLine> lines,
            Payloads.ShippingAddress shippingAddress
    ) {}
}
