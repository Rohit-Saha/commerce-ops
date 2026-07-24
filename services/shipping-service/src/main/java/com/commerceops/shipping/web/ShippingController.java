package com.commerceops.shipping.web;

import com.commerceops.common.web.ApiMessage;
import com.commerceops.common.web.RawResponse;
import com.commerceops.shipping.carrier.ShiprocketShippingProvider;
import com.commerceops.shipping.config.ChaosSettings;
import com.commerceops.shipping.service.ShippingService;
import com.commerceops.shipping.web.dto.ChaosResponse;
import com.commerceops.shipping.web.dto.CreateShipmentRequest;
import com.commerceops.shipping.web.dto.ShipmentEventResponse;
import com.commerceops.shipping.web.dto.ShipmentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
public class ShippingController {

    private final ShippingService shippingService;
    private final ChaosSettings chaosSettings;
    private final ShiprocketShippingProvider shiprocketShippingProvider;
    private final ObjectMapper objectMapper;

    public ShippingController(
            ShippingService shippingService,
            ChaosSettings chaosSettings,
            ShiprocketShippingProvider shiprocketShippingProvider,
            ObjectMapper objectMapper) {
        this.shippingService = shippingService;
        this.chaosSettings = chaosSettings;
        this.shiprocketShippingProvider = shiprocketShippingProvider;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<ShipmentResponse> list() {
        return shippingService.listAll().stream().map(ShipmentResponse::from).toList();
    }

    @ApiMessage("Shipment created")
    @PostMapping
    public ResponseEntity<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request) {
        var shipment = shippingService.createShipmentForOrder(request.orderId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ShipmentResponse.from(shipment, shippingService.events(shipment.getId())));
    }

    @GetMapping("/by-order/{orderId}")
    public List<ShipmentResponse> byOrder(@PathVariable("orderId") String orderId) {
        return shippingService.listByOrder(orderId).stream()
                .map(s -> ShipmentResponse.from(s, shippingService.events(s.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ShipmentResponse get(@PathVariable Long id) {
        return ShipmentResponse.from(shippingService.get(id), shippingService.events(id));
    }

    @GetMapping("/{id}/events")
    public List<ShipmentEventResponse> events(@PathVariable Long id) {
        return shippingService.events(id).stream().map(ShipmentEventResponse::from).toList();
    }

    @ApiMessage("Shipment status advanced")
    @PostMapping("/{id}/advance")
    public ShipmentResponse advance(@PathVariable Long id) {
        return ShipmentResponse.from(shippingService.advance(id), shippingService.events(id));
    }

    @PostMapping("/chaos")
    public ChaosResponse setChaos(@RequestParam("failureRate") double failureRate) {
        chaosSettings.setFailureRate(failureRate);
        return new ChaosResponse(chaosSettings.getFailureRate());
    }

    @RawResponse
    @PostMapping("/webhooks/shiprocket")
    public ResponseEntity<Map<String, String>> shiprocketWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "x-api-key", required = false) String apiKeyAlt,
            @RequestHeader(value = "X-Shiprocket-Token", required = false) String token) {
        String provided = firstNonBlank(token, apiKey, apiKeyAlt);
        if (!shiprocketShippingProvider.verifyWebhookToken(provided)) {
            return ResponseEntity.status(401).body(Map.of("status", "unauthorized"));
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String awb = firstNonBlank(
                    text(root, "awb"),
                    text(root, "awb_code"),
                    text(root.path("shipment"), "awb"),
                    text(root.path("current_status"), "awb"));
            String orderId = firstNonBlank(
                    text(root, "sr_order_id"),
                    text(root, "order_id"),
                    text(root.path("shipment"), "order_id"));
            String status = firstNonBlank(
                    text(root, "current_status"),
                    text(root, "shipment_status"),
                    text(root.path("current_status"), "status"),
                    text(root, "status"));
            String message = firstNonBlank(text(root, "current_timestamp"), status);
            String ref = firstNonBlank(awb, orderId);
            if (ref != null) {
                shippingService.applyCarrierStatus(ref, status, message, Instant.now());
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "invalid payload"));
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
