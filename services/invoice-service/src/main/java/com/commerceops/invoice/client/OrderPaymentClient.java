package com.commerceops.invoice.client;

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
public class OrderPaymentClient {

    private final RestClient orderRestClient;
    private final RestClient paymentRestClient;
    private final CircuitBreaker orderCircuit;
    private final CircuitBreaker paymentCircuit;

    public OrderPaymentClient(
            @Qualifier("orderRestClient") RestClient orderRestClient,
            @Qualifier("paymentRestClient") RestClient paymentRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderRestClient = orderRestClient;
        this.paymentRestClient = paymentRestClient;
        this.orderCircuit = circuitBreakerRegistry.circuitBreaker("order");
        this.paymentCircuit = circuitBreakerRegistry.circuitBreaker("payment");
    }

    public OrderSnapshot fetchOrder(String orderId) {
        try {
            JsonNode node = CircuitBreaker.decorateSupplier(orderCircuit, () ->
                    orderRestClient.get()
                            .uri("/api/orders/{id}", orderId)
                            .retrieve()
                            .body(JsonNode.class)).get();
            if (node == null) {
                throw new IllegalStateException("Empty order response for " + orderId);
            }
            return OrderSnapshot.from(node);
        } catch (CallNotPermittedException ex) {
            throw BusinessException.serviceUnavailable(
                    "Order service temporarily unavailable; try again shortly.");
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Failed to load order " + orderId + ": unreachable", ex);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Failed to load order " + orderId + ": " + ex.getStatusCode(), ex);
        }
    }

    public String fetchPaymentRef(String orderId) {
        try {
            JsonNode arr = CircuitBreaker.decorateSupplier(paymentCircuit, () ->
                    paymentRestClient.get()
                            .uri("/api/payments/by-order/{orderId}", orderId)
                            .retrieve()
                            .body(JsonNode.class)).get();
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return null;
            }
            for (JsonNode payment : arr) {
                if ("CAPTURED".equalsIgnoreCase(text(payment, "status"))) {
                    String providerPaymentId = text(payment, "providerPaymentId");
                    if (providerPaymentId != null && !providerPaymentId.isBlank()) {
                        return providerPaymentId;
                    }
                    return String.valueOf(payment.path("id").asLong());
                }
            }
            JsonNode first = arr.get(0);
            String providerPaymentId = text(first, "providerPaymentId");
            if (providerPaymentId != null && !providerPaymentId.isBlank()) {
                return providerPaymentId;
            }
            return first.has("id") ? String.valueOf(first.get("id").asLong()) : null;
        } catch (CallNotPermittedException | ResourceAccessException | RestClientResponseException ex) {
            return null;
        }
    }

    public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {}

    public record ShippingAddress(
            String recipientName,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country
    ) {}

    public record OrderSnapshot(
            String id,
            String customerId,
            BigDecimal totalAmount,
            String currency,
            List<OrderLine> lines,
            ShippingAddress shippingAddress
    ) {
        static OrderSnapshot from(JsonNode node) {
            List<OrderLine> lines = new ArrayList<>();
            JsonNode linesNode = node.path("lines");
            if (linesNode.isArray()) {
                for (JsonNode line : linesNode) {
                    lines.add(new OrderLine(
                            text(line, "sku"),
                            line.path("quantity").asInt(),
                            line.path("unitPrice").decimalValue()));
                }
            }
            ShippingAddress address = null;
            JsonNode ship = node.path("shippingAddress");
            if (ship.isObject() && !ship.isEmpty()) {
                address = new ShippingAddress(
                        text(ship, "recipientName"),
                        text(ship, "line1"),
                        text(ship, "line2"),
                        text(ship, "city"),
                        text(ship, "state"),
                        text(ship, "postalCode"),
                        text(ship, "country"));
            }
            return new OrderSnapshot(
                    text(node, "id"),
                    text(node, "customerId"),
                    node.path("totalAmount").decimalValue(),
                    text(node, "currency") != null ? text(node, "currency") : "INR",
                    lines,
                    address);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }
}
