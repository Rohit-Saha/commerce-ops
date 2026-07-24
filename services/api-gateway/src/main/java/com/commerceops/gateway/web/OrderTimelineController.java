package com.commerceops.gateway.web;

import com.commerceops.gateway.service.ProxyGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fans a single order id out to order/saga/payment/shipping and stitches the results into
 * one admin-friendly JSON document. The order lookup is authoritative (a 404 there fails
 * the whole request); the other three are best-effort since a saga/payment/shipment may
 * not exist yet for a freshly created order.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderTimelineController {

    private static final Logger log = LoggerFactory.getLogger(OrderTimelineController.class);

    private final RestClient orderClient;
    private final RestClient sagaClient;
    private final RestClient paymentClient;
    private final RestClient shippingClient;
    private final ProxyGateway proxy;

    public OrderTimelineController(
            @Qualifier("orderRestClient") RestClient orderClient,
            @Qualifier("sagaRestClient") RestClient sagaClient,
            @Qualifier("paymentRestClient") RestClient paymentClient,
            @Qualifier("shippingRestClient") RestClient shippingClient,
            ProxyGateway proxy) {
        this.orderClient = orderClient;
        this.sagaClient = sagaClient;
        this.paymentClient = paymentClient;
        this.shippingClient = shippingClient;
        this.proxy = proxy;
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<Map<String, Object>> timeline(@PathVariable String id) {
        String encodedId = UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);

        // Authoritative: propagates the downstream status (e.g. 404) if the order is missing.
        JsonNode order = ApiBodies.data(proxy.get(orderClient, "/api/orders/" + encodedId).getBody());

        JsonNode saga = ApiBodies.data(tryGet(sagaClient, "/api/sagas/by-order/" + encodedId));
        JsonNode payments = ApiBodies.data(tryGet(paymentClient, "/api/payments/by-order/" + encodedId));
        JsonNode shipments = ApiBodies.data(tryGet(shippingClient, "/api/shipments/by-order/" + encodedId));

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("orderId", id);
        timeline.put("order", order);
        timeline.put("saga", saga);
        timeline.put("payments", payments);
        timeline.put("shipments", shipments);
        return ResponseEntity.ok(timeline);
    }

    private JsonNode tryGet(RestClient client, String uri) {
        try {
            return proxy.get(client, uri).getBody();
        } catch (RestClientResponseException ex) {
            log.debug("Timeline sub-request {} returned {}, treating as absent", uri, ex.getStatusCode());
            return null;
        } catch (Exception ex) {
            log.warn("Timeline sub-request {} failed: {}", uri, ex.getMessage());
            return null;
        }
    }
}
