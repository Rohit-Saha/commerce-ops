package com.commerceops.gateway.web;

import com.commerceops.common.web.RawResponse;
import com.commerceops.gateway.service.ProxyGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/shipments")
public class ShippingProxyController {

    private final RestClient shippingClient;
    private final ProxyGateway proxy;

    public ShippingProxyController(@Qualifier("shippingRestClient") RestClient shippingClient, ProxyGateway proxy) {
        this.shippingClient = shippingClient;
        this.proxy = proxy;
    }

    @GetMapping
    public ResponseEntity<JsonNode> list() {
        return proxy.get(shippingClient, "/api/shipments");
    }

    @PostMapping
    public ResponseEntity<JsonNode> create(@RequestBody(required = false) JsonNode body) {
        return proxy.post(shippingClient, "/api/shipments", body, null);
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<JsonNode> byOrder(@PathVariable String orderId) {
        return proxy.get(shippingClient, "/api/shipments/by-order/" + encode(orderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> get(@PathVariable String id) {
        return proxy.get(shippingClient, "/api/shipments/" + encode(id));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<JsonNode> events(@PathVariable String id) {
        return proxy.get(shippingClient, "/api/shipments/" + encode(id) + "/events");
    }

    @PostMapping("/{id}/advance")
    public ResponseEntity<JsonNode> advance(@PathVariable String id) {
        return proxy.post(shippingClient, "/api/shipments/" + encode(id) + "/advance", null, null);
    }

    @PostMapping("/chaos")
    public ResponseEntity<JsonNode> setChaos(@RequestParam double failureRate) {
        return proxy.post(shippingClient, "/api/shipments/chaos?failureRate=" + failureRate, null, null);
    }

    @RawResponse
    @PostMapping("/webhooks/shiprocket")
    public ResponseEntity<JsonNode> shiprocketWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Shiprocket-Token", required = false) String token,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.add("X-Shiprocket-Token", token);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            headers.add("X-Api-Key", apiKey);
        }
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return proxy.post(shippingClient, "/api/shipments/webhooks/shiprocket", body, headers);
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
